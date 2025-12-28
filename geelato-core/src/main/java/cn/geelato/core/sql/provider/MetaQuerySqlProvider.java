package cn.geelato.core.sql.provider;

import cn.geelato.core.gql.command.QueryCommand;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.TableForeign;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.meta.model.field.FunctionFieldValue;
import cn.geelato.core.meta.model.parser.FunctionParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.util.StringUtils;
import java.util.regex.Pattern;

/**
 * @author geemeta
 */
@Slf4j
public class MetaQuerySqlProvider extends MetaBaseSqlProvider<QueryCommand> {
    @Override
    protected Object[] buildParams(QueryCommand command) {
        return buildWhereParams(command);
    }

    @Override
    protected int[] buildTypes(QueryCommand command) {
        return buildWhereTypes(command);
    }

    @Override
    protected String buildOneSql(QueryCommand command) {
        StringBuilder sb = new StringBuilder();
        EntityMeta md = getEntityMeta(command);
        String mainAlias = null;
        if (command.getForeignFields() != null && command.getForeignFields().length > 0) {
            mainAlias = buildTableAlias(md.getTableName());
            md.setTableAlias(mainAlias);
        }else{
            md.setTableAlias(null);
        }
        sb.append("select ");
        buildSelectFields(sb, md, command);
        sb.append(" from ");
        sb.append(md.getTableName());
        if (mainAlias != null) {
            sb.append(" ");
            sb.append(mainAlias);
        }
        buildJoins(sb, md, command);
        FilterGroup fg = command.getWhere();
        if (fg != null && fg.getFilters() != null && !fg.getFilters().isEmpty()) {
            sb.append(" where ");
            buildConditions(sb, md, fg);
        }
        if (command.getOriginalWhere() != null) {
            sb.append("  and  (");
            String ow = command.getOriginalWhere();
            if (!"1=1".equals(ow) && md.getTableAlias() != null) {
                ow = decorateOriginalWhere(md, ow);
            }
            sb.append(ow);
            sb.append("  )");
        }
        if (StringUtils.hasText(command.getGroupBy())) {
            sb.append(" group by ");
            sb.append(command.getGroupBy());
        }
        if (command.getHaving() != null) {
            sb.append(" having ");
            sb.append(command.getHaving());
        }
        if (StringUtils.hasText(command.getOrderBy())) {
            sb.append(" order by ");
            sb.append(command.getOrderBy());
        }
        if (command.isPagingQuery()) {
            sb.append(" limit ");
            sb.append((command.getPageNum() - 1) * command.getPageSize());
            sb.append(",");
            sb.append(command.getPageSize());
        }
        return sb.toString();
    }

    /**
     * 构建统计数据查询的SQL语句。
     * <p>
     * 根据提供的查询命令（QueryCommand），构建一个用于统计数据的SQL查询语句。
     *
     * @param command 查询命令对象，包含查询所需的各种参数和条件
     * @return 构建好的SQL语句字符串
     */
    public String buildCountSql(QueryCommand command) {
        StringBuilder sb = new StringBuilder();
        EntityMeta md = getEntityMeta(command);
        sb.append("select count(*) from (");
        sb.append("select ");
        buildSelectFields(sb, md, command);
        sb.append(" from ");
        sb.append(md.getTableName());
        if (command.getForeignFields() != null && command.getForeignFields().length > 0) {
            String mainAlias = md.getTableAlias() != null ? md.getTableAlias() : buildTableAlias(md.getTableName());
            md.setTableAlias(mainAlias);
            sb.append(" ");
            sb.append(mainAlias);
        }else{
            md.setTableAlias(null);
        }
        buildJoins(sb, md, command);
        // where
        FilterGroup fg = command.getWhere();
        if (fg != null && fg.getFilters() != null && !fg.getFilters().isEmpty()) {
            sb.append(" where ");
            buildConditions(sb, md, fg);
        }
        if (command.getOriginalWhere() != null) {
            sb.append("  and  (");
            String ow = command.getOriginalWhere();
            if (!"1=1".equals(ow) && md.getTableAlias() != null) {
                ow = decorateOriginalWhere(md, ow);
            }
            sb.append(ow);
            sb.append(" )");
        }
        // group by
        if (StringUtils.hasText(command.getGroupBy())) {
            sb.append(" group by ");
            sb.append(command.getGroupBy());
        }
        // having
        if (command.getHaving() != null) {
            sb.append(" having ");
            sb.append(command.getHaving());
        }
        sb.append(") t");
        return sb.toString();
    }

    private void buildSelectCountField(StringBuilder sb, EntityMeta md, String[] fields) {
        if (fields == null || fields.length == 0) {
            sb.append("*");
            return;
        } else if (fields.length == 1 && "*".equals(fields[0])) {
            sb.append("*");
            return;
        }
        // 只取第一个字段用于统计总数，默认按主键统计
        if (md.getId() != null) {
            tryAppendKeywords(sb, md.getId().getColumnName());
        } else {
            FieldMeta fm = md.getFieldMeta(fields[0]);
        }
    }

    private void buildSelectFields(StringBuilder sb, EntityMeta md, QueryCommand command) {

        if (command.getFields() == null || command.getFields().length == 0) {
            sb.append("*");
            return;
        } else if (command.getFields().length == 1 && "*".equals(command.getFields()[0])) {
            sb.append("*");
            return;
        }

        for (String fieldName : command.getFields()) {
            // 忽略的字段不查询
            if (ArrayUtils.contains(command.getIgnoreFields(), fieldName)) {
                continue;
            }
            if (FunctionParser.isFunction(fieldName)) {
                String afterRefaceExpression = FunctionParser.reconstruct(fieldName, md.getEntityName());
                sb.append(new FunctionFieldValue(afterRefaceExpression).getMysqlFunction()).append(" ");
            } else {
                if (command.getForeignFields() != null && ArrayUtils.contains(command.getForeignFields(), fieldName)) {
                    TableForeign tf = findForeignByMainField(md, fieldName);
                    if (tf != null) {
                        String foreignAlias = buildTableAlias(tf.getForeignTable());
                        EntityMeta foreignEm = metaManager.get(tf.getForeignTable());
                        FieldMeta displayFm = chooseDisplayField(foreignEm);
                        if (displayFm != null) {
                            sb.append(foreignAlias);
                            sb.append(".");
                            tryAppendKeywords(sb, displayFm.getColumnName());
                            sb.append(" ");
                            Object aliasVal = command.getAlias().get(fieldName);
                            String aliasName = aliasVal != null ? aliasVal.toString() : displayFm.getFieldName();
                            tryAppendKeywords(sb, aliasName);
                            sb.append(",");
                            continue;
                        }
                    }
                    // 若未找到外键或展示字段，回退为主表字段输出
                }
                FieldMeta fm = md.getFieldMeta(fieldName);
                if (command.getAlias().containsKey(fieldName)) {
                    tryAppendKeywords(md, sb, fm);
                    sb.append(" ");
                    tryAppendKeywords(sb, command.getAlias().get(fieldName).toString());
                } else {
                    if (fm.isEquals()) {
                        tryAppendKeywords(md, sb, fm);
                    } else {
                        tryAppendKeywords(md, sb, fm);
                        sb.append(" ");
                        tryAppendKeywords(sb, fm.getFieldName());
                    }
                }
            }
            sb.append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
    }

    private void buildJoins(StringBuilder sb, EntityMeta md, QueryCommand command) {
        if (command.getForeignFields() == null) {
            return;
        }
        for (String fieldName : command.getForeignFields()) {
            TableForeign tf = findForeignByMainField(md, fieldName);
            if (tf != null && tf.getEnableStatus() == 1) {
                String foreignAlias = buildTableAlias(tf.getForeignTable());
                sb.append(" left join ");
                sb.append(tf.getForeignTable());
                sb.append(" ");
                sb.append(foreignAlias);
                sb.append(" on ");
                sb.append(md.getTableAlias() != null ? md.getTableAlias() : md.getTableName());
                sb.append(".");
                tryAppendKeywords(sb, tf.getMainTableCol());
                sb.append("=");
                sb.append(foreignAlias);
                sb.append(".");
                tryAppendKeywords(sb, tf.getForeignTableCol());
            }
        }
    }

    private TableForeign findForeignByMainField(EntityMeta md, String fieldName) {
        String mainCol = md.getColumnName(fieldName);
        if (md.getTableForeigns() != null) {
            for (TableForeign tf : md.getTableForeigns()) {
                if (tf.getEnableStatus() == 1 && mainCol.equalsIgnoreCase(tf.getMainTableCol())) {
                    return tf;
                }
            }
        }
        return null;
    }

    private FieldMeta chooseDisplayField(EntityMeta foreignEm) {
        if (foreignEm == null) {
            return null;
        }
        FieldMeta name = foreignEm.containsField("name") ? foreignEm.getFieldMeta("name") : null;
        if (name != null) return name;
        FieldMeta title = foreignEm.containsField("title") ? foreignEm.getFieldMeta("title") : null;
        if (title != null) return title;
        return foreignEm.getId();
    }

    private String decorateOriginalWhere(EntityMeta md, String originalWhere) {
        String res = originalWhere;
        String alias = md.getTableAlias();
        if (alias == null) {
            return res;
        }
        for (FieldMeta fm : md.getFieldMetas()) {
            String col = fm.getColumnMeta().getName();
            String pattern = "(?<!\\.)\\b" + Pattern.quote(col) + "\\b";
            res = res.replaceAll(pattern, alias + "." + col);
        }
        return res;
    }
}
