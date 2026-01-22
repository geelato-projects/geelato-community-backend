package cn.geelato.core.sql.provider;

import cn.geelato.core.mql.TypeConverter;
import cn.geelato.core.mql.command.BaseCommand;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import org.apache.commons.collections.map.HashedMap;
import org.springframework.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        BoundSql boundSql = new BoundSql();
        boundSql.setName(command.getEntityName());
        String sql = buildOneSql(command);
//        sql = sanitizeSql(sql);
        boundSql.setSql(sql);
        command.setFinalSql(sql);
        Object[] params = buildParams(command);
//        params = sanitizeParams(params);
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
                String fieldType=getEntityMeta(command).getFieldMeta(filter.getField()).getColumnMeta().getDataType();
                Assert.isTrue(!"JSON".equals(fieldType), filter.getField() + "为JSON,不支持" + filter.getOperator());
                list.add(filter.getValue());
            }else {
                list.add(filter.getValue());
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
        int[] types = new int[command.getWhere().getFilters().size()];
        int i = 0;
        for (FilterGroup.Filter filter : command.getWhere().getFilters()) {
            if (filter.getFilterFieldType() != FilterGroup.FilterFieldType.Function) {
                types[i] = TypeConverter.toSqlType(em.getFieldMeta(filter.getField()).getColumnMeta().getDataType());
                i++;
            }
        }
        return types;
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
            FieldMeta fm = em.getFieldMeta(filter.getField());
            ConditionOperator.from(filter.getOperator())
                    .appendField(this, sb, em, fm, filter);
        }
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
