package cn.geelato.core.sql.provider;

import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.core.mql.TypeConverter;
import cn.geelato.core.mql.command.BaseCommand;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.sql.InvalidFilterFieldException;
import cn.geelato.utils.StringUtils;
import org.apache.commons.collections.map.HashedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @author geemeta
 */
@SuppressWarnings("rawtypes")
public abstract class MetaBaseSqlProvider<E extends BaseCommand> {
    protected Boolean LogicDelete = true;  // 是否开启软删除
    protected Boolean PermissionControl = true;
    protected static final HashedMap keywordsMap = new HashedMap();
    protected static final Map<FilterGroup.Operator, String> enumToSignString = new HashMap<FilterGroup.Operator, String>();
    private static volatile String primaryDbTypeCache;
    private static volatile boolean primaryDbTypeResolved = false;
    private final ThreadLocal<BaseCommand> currentCommandHolder = new ThreadLocal<>();
    protected MetaManager metaManager = MetaManager.singleInstance();
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private final Map<String, String> tableAlias = new HashMap<>(8);
    protected String functionSchema = "geelato";

    static {
        keywordsMap.put("index", true);
        keywordsMap.put("inner", true);
        keywordsMap.put("enable", true);
        keywordsMap.put("key", true);

    }

    protected static String convertToSignString(FilterGroup.Operator operator) {
        if (operator == FilterGroup.Operator.eq) {
            return "=";
        } else if (operator == FilterGroup.Operator.neq) {
            return "!=";
        } else if (operator == FilterGroup.Operator.lt) {
            return "<";
        } else if (operator == FilterGroup.Operator.lte) {
            return "<=";
        } else if (operator == FilterGroup.Operator.gt) {
            return ">";
        } else if (operator == FilterGroup.Operator.gte) {
            return ">=";
        } else if (operator == FilterGroup.Operator.in) {
            return "in";
        } else if (operator == FilterGroup.Operator.notin) {
            return "not in";
        }

        return "=";
    }


    static {
        for (FilterGroup.Operator operator : FilterGroup.Operator.values()) {
            enumToSignString.put(operator, convertToSignString(operator));
        }
    }

    /**
     * 对command进行递归解析，创建BoundSql对象及其BoundSql子对象
     *
     * @param command QueryCommand、UpdateCommand等
     */
    @SuppressWarnings("unchecked")
    public BoundSql generate(E command) {
        BaseCommand previousCommand = currentCommandHolder.get();
        currentCommandHolder.set(command);
        try {
            BoundSql boundSql = new BoundSql();
            boundSql.setName(command.getEntityName());
            String sql = buildOneSql(command);
            boundSql.setSql(sql);
            command.setFinalSql(sql);
            Object[] params = buildParams(command);
            boundSql.setParams(params);
            boundSql.setTypes(buildTypes(command));
            logger.info("final-sql: {}", command.getFinalSql());
            if (command.getCommands() != null) {
                command.getCommands().forEach(item -> {
                    BoundSql subBoundSql = generate((E)item);
                    if (boundSql.getBoundSqlMap() == null) {
                        boundSql.setBoundSqlMap(new HashMap<>());
                    }
                    boundSql.getBoundSqlMap().put(subBoundSql.getName(), subBoundSql);
                });
            }
            boundSql.setCommand(command);
            return boundSql;
        } finally {
            if (previousCommand != null) {
                currentCommandHolder.set(previousCommand);
            } else {
                currentCommandHolder.remove();
            }
        }
    }

    protected abstract Object[] buildParams(E command);

    protected abstract int[] buildTypes(E command);

    /**
     * 构建一条语句，如insert、save、select、delete语句，在子类中实现
     *
     * @return 带参数（?）或无参数的完整sql语句
     */
    protected abstract String buildOneSql(E command);


    /**
     * 基于条件部分，构建参数值对像数组
     * 对于update、insert、delete的sql provider，即结合字段设值部分的需要，组合调整
     */
    protected Object[] buildWhereParams(E command) {
        if (command.getWhere() == null || command.getWhere().getFilters() == null || command.getWhere().getFilters().isEmpty()) {
            return new Object[0];
        }
        List<Object> list = new ArrayList<>();
        for (FilterGroup.Filter filter : command.getWhere().getFilters()) {
            recombine(filter, list, command);
        }
        List<Object> newList = recursionFilterGroup(command.getWhere().getChildFilterGroup(), command, list);
        return newList.toArray();
    }

    private void recombine(FilterGroup.Filter filter, List<Object> list, E command) {
        // 若为in操作，则需将in内的内容拆分成多个，相应地在构建参数占位符的地方也做相应的处理
        if (filter.getOperator().equals(FilterGroup.Operator.in) || filter.getOperator().equals(FilterGroup.Operator.notin)) {
            Object[] ary = filter.getValueAsArray();
            list.addAll(Arrays.asList(ary));
        } else if (filter.getOperator().equals(FilterGroup.Operator.nil)
                || filter.getOperator().equals(FilterGroup.Operator.bt)
                ||filter.getOperator().equals(FilterGroup.Operator.fis)) {
            // not do anything
        }else {
            if(filter.getFilterFieldType()== FilterGroup.FilterFieldType.Normal){
                FieldMeta fieldMeta = requireFilterFieldMeta(getEntityMeta(command), filter, "buildWhereParams");
                String fieldType = fieldMeta.getColumnMeta().getDataType();
                Assert.isTrue(!"JSON".equals(fieldType), filter.getField() + "为JSON,不支持" + filter.getOperator());
                list.add(filter.getRawValue() != null ? filter.getRawValue() : filter.getValue());
            }else {
                list.add(filter.getRawValue() != null ? filter.getRawValue() : filter.getValue());
            }
        }
    }

    private List<Object> recursionFilterGroup(List<FilterGroup> childFilterGroup, E command, List<Object> list) {
        for (FilterGroup filterGroup : childFilterGroup) {
            for (FilterGroup.Filter filter : filterGroup.getFilters()) {
                recombine(filter, list, command);
            }
            if (!filterGroup.getChildFilterGroup().isEmpty()) {
                recursionFilterGroup(filterGroup.getChildFilterGroup(), command, list);
            }
        }
        return list;
    }

    /**
     * 基于条件部分，构建参数类型数组。
     * <p>
     * 对于update、insert、delete的sql provider，该方法会根据字段设值部分的需要，对条件部分进行调整，并构建相应的参数类型数组。
     *
     * @param command 命令对象，包含SQL操作的相关信息
     * @return 返回构建好的参数类型数组
     */
    protected int[] buildWhereTypes(E command) {
        if (command.getWhere() == null || command.getWhere().getFilters() == null) {
            return new int[0];
        }
        EntityMeta em = getEntityMeta(command);
        List<Integer> typeList = new ArrayList<>();
        appendWhereTypes(command.getWhere(), em, typeList);
        int[] types = new int[typeList.size()];
        for (int i = 0; i < typeList.size(); i++) {
            types[i] = typeList.get(i);
        }
        return types;
    }

    private void appendWhereTypes(FilterGroup filterGroup, EntityMeta em, List<Integer> typeList) {
        if (filterGroup == null) {
            return;
        }
        if (filterGroup.getFilters() != null) {
            for (FilterGroup.Filter filter : filterGroup.getFilters()) {
                appendWhereType(filter, em, typeList);
            }
        }
        if (filterGroup.getChildFilterGroup() != null) {
            for (FilterGroup childFilterGroup : filterGroup.getChildFilterGroup()) {
                appendWhereTypes(childFilterGroup, em, typeList);
            }
        }
    }

    private void appendWhereType(FilterGroup.Filter filter, EntityMeta em, List<Integer> typeList) {
        if (filter.getOperator().equals(FilterGroup.Operator.in) || filter.getOperator().equals(FilterGroup.Operator.notin)) {
            // in/notin 会产生多个参数，需要为每个参数添加类型
            Object[] ary = filter.getValueAsArray();
            if (filter.getFilterFieldType() != FilterGroup.FilterFieldType.Function) {
                int sqlType = resolveFilterSqlType(filter, em);
                for (int j = 0; j < ary.length; j++) {
                    typeList.add(sqlType);
                }
            } else {
                for (int j = 0; j < ary.length; j++) {
                    typeList.add(java.sql.Types.VARCHAR);
                }
            }
        } else if (filter.getOperator().equals(FilterGroup.Operator.nil)
                || filter.getOperator().equals(FilterGroup.Operator.bt)
                || filter.getOperator().equals(FilterGroup.Operator.fis)) {
            // nil/bt/fis 不产生参数，跳过
        } else {
            if (isPatternMatchOperator(filter.getOperator())) {
                // like/contains 类查询一律按字符串绑定，避免数值列模糊匹配时尝试把关键字转为数字
                typeList.add(java.sql.Types.VARCHAR);
            } else if (filter.getFilterFieldType() != FilterGroup.FilterFieldType.Function) {
                typeList.add(resolveFilterSqlType(filter, em));
            } else {
                typeList.add(java.sql.Types.VARCHAR);
            }
        }
    }

    private boolean isPatternMatchOperator(FilterGroup.Operator operator) {
        return FilterGroup.Operator.startWith.equals(operator)
                || FilterGroup.Operator.endWith.equals(operator)
                || FilterGroup.Operator.contains.equals(operator);
    }

    private int resolveFilterSqlType(FilterGroup.Filter filter, EntityMeta em) {
        if (filter == null || em == null || filter.getField() == null) {
            return java.sql.Types.VARCHAR;
        }
        FieldMeta fieldMeta = requireFilterFieldMeta(em, filter, "buildWhereTypes");
        if (fieldMeta.getColumnMeta() == null) {
            return java.sql.Types.VARCHAR;
        }
        return TypeConverter.toSqlType(fieldMeta.getColumnMeta().getDataType());
    }

    /**
     * 只构建当前实体的查询条件!isRefField
     */
    protected void buildConditions(StringBuilder sb, EntityMeta em, List<FilterGroup.Filter> list, FilterGroup.Logic logic) {
        if (list != null && !list.isEmpty()) {
            Iterator<FilterGroup.Filter> iterator = list.iterator();
            int index = 0;
            while (iterator.hasNext()) {
                FilterGroup.Filter filter = iterator.next();
                // 只构建当前实体的查询条件
                if (filter.isRefField()) {
                    continue;
                }
                if (index > 0) {
                    sb.append(" ");
                    sb.append(logic.getText());
                    sb.append(" ");
                }
                buildConditionSegment(sb, em, filter);
                index += 1;
            }
        }
    }

    protected void buildConditions(StringBuilder sb, EntityMeta em, FilterGroup filterGroup) {
        List<FilterGroup.Filter> list = filterGroup.getFilters();
        if (list != null && !list.isEmpty()) {
            Iterator<FilterGroup.Filter> iterator = list.iterator();
            int index = 0;
            while (iterator.hasNext()) {
                FilterGroup.Filter filter = iterator.next();
                if (filter.isRefField()) {
                    continue;
                }
                if (index > 0) {
                    sb.append(" ");
                    sb.append(filterGroup.getLogic().getText());
                    sb.append(" ");
                }
                buildConditionSegment(sb, em, filter);
                index += 1;
            }
        }

        buildChildConditions(sb, em, filterGroup, FilterGroup.Logic.and);
    }

    protected void buildChildConditions(StringBuilder sb, EntityMeta em, FilterGroup filterGroup, FilterGroup.Logic logic) {
        List<FilterGroup> childFilterGroup = filterGroup.getChildFilterGroup();
        if (childFilterGroup != null && !childFilterGroup.isEmpty()) {
            for (FilterGroup fg : filterGroup.getChildFilterGroup()) {
                sb.append(String.format(" %s ", logic == null ? "and" : logic.getText()));
                sb.append(" ( ");
                buildConditions(sb, em, fg.getFilters(), fg.getLogic());
                if (fg.getChildFilterGroup() != null) {
                    buildChildConditions(sb, em, fg, fg.getLogic());
                }
                sb.append(" ) ");
            }
        }
    }

    /**
     * 构建单个过滤条件
     */
    protected void buildConditionSegment(StringBuilder sb, EntityMeta em, FilterGroup.Filter filter) {
        if (filter.getFilterFieldType() == FilterGroup.FilterFieldType.Function) {
            ConditionOperator.from(filter.getOperator())
                    .appendFunction(this, sb, em, filter.getField(), filter);
        } else {
            FieldMeta fm = requireFilterFieldMeta(em, filter, "buildConditionSegment");
            ConditionOperator.from(filter.getOperator())
                    .appendField(this, sb, em, fm, filter);
        }
    }

    private FieldMeta requireFilterFieldMeta(EntityMeta em, FilterGroup.Filter filter, String scene) {
        if (em == null) {
            throw new IllegalArgumentException("EntityMeta不能为空");
        }
        if (filter == null) {
            throw new IllegalArgumentException("过滤条件不能为空");
        }
        if (filter.getFilterFieldType() == FilterGroup.FilterFieldType.Function || !StringUtils.hasText(filter.getField())) {
            return null;
        }
        FieldMeta fieldMeta = em.getFieldMeta(filter.getField());
        if (fieldMeta == null) {
            throw new InvalidFilterFieldException(em.getEntityName(), filter.getField(), filter.getOperator(), scene);
        }
        return fieldMeta;
    }

    protected boolean isKeywords(String field) {
        if (field == null) {
            return false;
        }
        return keywordsMap.containsKey(field);
    }

    protected void tryAppendKeywords(EntityMeta em, StringBuilder sb, FieldMeta fm) {
        Assert.notNull(fm, "获取不到元数据，fieldName：" + fm.getFieldName());
        if (em.getTableAlias() != null) {
            sb.append(em.getTableAlias());
            sb.append(".");
        }
        this.tryAppendKeywords(sb, fm.getColumnName());
    }

    protected void tryAppendKeywords(StringBuilder sb, String field) {
        if (isKeywords(field)) {
            sb.append("'");
            sb.append(field);
            sb.append("'");
        } else {
            sb.append(field);
        }
    }

    protected String resolveOrderBy(EntityMeta em, String orderBy) {
        String[] items = orderBy.split(",");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < items.length; i++) {
            if (i > 0) {
                result.append(",");
            }
            String item = items[i].trim();
            if (item.isEmpty()) {
                continue;
            }
            String[] parts = item.split("\\s+");
            String columnName = resolveOrderByColumn(em, parts[0]);
            appendQuotedIdentifier(result, em, columnName);
            for (int j = 1; j < parts.length; j++) {
                result.append(" ").append(parts[j]);
            }
        }
        return result.toString();
    }

    protected String resolveOrderByColumn(EntityMeta em, String fieldOrColumn) {
        if (em == null || !StringUtils.hasText(fieldOrColumn)) {
            return fieldOrColumn;
        }
        if (em.containsField(fieldOrColumn)) {
            return em.getFieldMeta(fieldOrColumn).getColumnName();
        }
        try {
            FieldMeta fieldMeta = em.getFieldMetaByColumn(fieldOrColumn);
            if (fieldMeta != null) {
                return fieldMeta.getColumnName();
            }
        } catch (RuntimeException ignored) {
            // 回退为原始字段，交给调用方按字面值输出
        }
        return fieldOrColumn;
    }

    /**
     * 为标识符加引用符，保留大小写（不同数据库使用不同的引用符）
     * <ul>
     *   <li>PostgreSQL/Oracle: 双引号 "identifier"</li>
     *   <li>MySQL: 反引号 `identifier`</li>
     *   <li>SQL Server: 方括号 [identifier]</li>
     * </ul>
     */
    protected void appendQuotedIdentifier(StringBuilder sb, EntityMeta em, String identifier) {
        String dbType = resolveEffectiveDbType(em);
        if ("mysql".equalsIgnoreCase(dbType)) {
            sb.append('`');
            sb.append(identifier);
            sb.append('`');
        } else if ("sqlserver".equalsIgnoreCase(dbType)) {
            sb.append('[');
            sb.append(identifier);
            sb.append(']');
        } else {
            // PostgreSQL, Oracle 及其他均使用双引号（ANSI SQL 标准）
            sb.append('"');
            sb.append(identifier);
            sb.append('"');
        }
    }

    private String resolveEffectiveDbType(EntityMeta em) {
        String dbType = (em != null && em.getTableMeta() != null) ? em.getTableMeta().getDbType() : null;
        dbType = normalizeDbType(dbType);
        if (StringUtils.hasText(dbType)) {
            return dbType;
        }
        BaseCommand currentCommand = currentCommandHolder.get();
        dbType = resolveDbTypeByConnectId(currentCommand != null ? currentCommand.getConnectId() : null);
        if (StringUtils.hasText(dbType)) {
            return dbType;
        }
        dbType = resolveDbTypeByConnectId(em != null && em.getTableMeta() != null ? em.getTableMeta().getConnectId() : null);
        if (StringUtils.hasText(dbType)) {
            return dbType;
        }
        dbType = resolveDbTypeByConnectId(DataSourceManager.singleInstance().getDefaultDataSourceKey());
        if (StringUtils.hasText(dbType)) {
            return dbType;
        }
        return resolvePrimaryDbType();
    }

    private String resolveDbTypeByConnectId(String connectId) {
        if (!StringUtils.hasText(connectId)) {
            return null;
        }
        try {
            DataSource dataSource = DataSourceManager.singleInstance().getDataSource(connectId);
            if (dataSource == null) {
                return null;
            }
            try (Connection connection = dataSource.getConnection()) {
                DatabaseMetaData metaData = connection.getMetaData();
                String dbType = normalizeDbType(metaData == null ? null : metaData.getDatabaseProductName());
                if (!StringUtils.hasText(dbType) && metaData != null) {
                    dbType = normalizeDbType(metaData.getURL());
                }
                return dbType;
            }
        } catch (Exception e) {
            logger.debug("resolve dbType by connectId failed, connectId={}", connectId, e);
            return null;
        }
    }

    private String resolvePrimaryDbType() {
        if (primaryDbTypeResolved) {
            return primaryDbTypeCache;
        }
        synchronized (MetaBaseSqlProvider.class) {
            if (primaryDbTypeResolved) {
                return primaryDbTypeCache;
            }
            DataSource dataSource = null;
            try {
                dataSource = DataSourceManager.singleInstance().getDataSource("primary");
            } catch (Exception e) {
                logger.debug("primary data source not available when resolving dbType", e);
            }
            if (dataSource != null) {
                try (Connection connection = dataSource.getConnection()) {
                    DatabaseMetaData metaData = connection.getMetaData();
                    primaryDbTypeCache = normalizeDbType(metaData == null ? null : metaData.getDatabaseProductName());
                    if (!StringUtils.hasText(primaryDbTypeCache) && metaData != null) {
                        primaryDbTypeCache = normalizeDbType(metaData.getURL());
                    }
                } catch (SQLException e) {
                    logger.warn("resolve primary dbType failed", e);
                }
            }
            primaryDbTypeResolved = true;
            return primaryDbTypeCache;
        }
    }

    private String normalizeDbType(String dbType) {
        if (!StringUtils.hasText(dbType)) {
            return null;
        }
        String normalized = dbType.trim().toLowerCase(Locale.ENGLISH);
        if (normalized.contains("mysql")) {
            return "mysql";
        }
        if (normalized.contains("sql server") || normalized.contains("sqlserver")) {
            return "sqlserver";
        }
        if (normalized.contains("postgres")) {
            return "postgresql";
        }
        if (normalized.contains("oracle")) {
            return "oracle";
        }
        return normalized;
    }

    public EntityMeta getEntityMeta(E command) {
        EntityMeta em = metaManager.getByEntityName(command.getEntityName());
        Assert.notNull(em, "未能通过entityName：" + command.getEntityName() + ",获取元数据信息EntityMeta。");
        return em;
    }

    public String buildTableAlias(String tableName) {
        if (tableName != null && !this.tableAlias.containsKey(tableName)) {
            this.tableAlias.put(tableName, "t" + this.tableAlias.size());
        }
        return this.tableAlias.get(tableName);
    }

    public String getTableAlias(String tableName) {
        return this.tableAlias.get(tableName);
    }

    public String decorateExpressionWithAlias(EntityMeta md, String expr) {
        String res = expr;
        String alias = md.getTableAlias();
        if (alias == null) {
            return res;
        }
        for (FieldMeta fm : md.getFieldMetas()) {
            String col = fm.getColumnMeta().getName();
            String pattern = "(?<!\\.)\\b" + java.util.regex.Pattern.quote(col) + "\\b";
            res = res.replaceAll(pattern, alias + "." + col);
        }
        return res;
    }

    public String qualifyFunction(String expr) {
        if (expr == null) {
            return null;
        }
        int idx = expr.indexOf("(");
        if (idx > 0) {
            String name = expr.substring(0, idx).trim();
            if (name.startsWith("gfn_") && !name.contains(".")) {
                return functionSchema + "." + expr;
            }
        }
        return expr;
    }
    
    private String sanitizeSql(String sql) {
        if (sql == null) {
            return null;
        }
        return sql.replaceAll("--.*?(\\r?\\n|$)", " ");
    }
    
    private Object[] sanitizeParams(Object[] params) {
        if (params == null || params.length == 0) {
            return params;
        }
        Object[] arr = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Object v = params[i];
            if (v instanceof String s) {
                arr[i] = sanitizeStringParam(s);
            } else {
                arr[i] = v;
            }
        }
        return arr;
    }
    
    private String sanitizeStringParam(String s) {
        String res = s;
        res = res.replaceAll("--.*?(\\r?\\n|$)", "");
        return res;
    }
}
