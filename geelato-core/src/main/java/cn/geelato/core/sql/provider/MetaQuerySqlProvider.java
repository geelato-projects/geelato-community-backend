package cn.geelato.core.sql.provider;

import cn.geelato.core.gql.command.QueryCommand;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.meta.model.field.FunctionFieldValue;
import cn.geelato.core.meta.model.parser.FunctionParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.util.StringUtils;

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
        sb.append("select ");
        buildSelectFields(sb, md, command);
        sb.append(" from ");
        sb.append(md.getTableName());
        // where
        FilterGroup fg = command.getWhere();
        if (fg != null && fg.getFilters() != null && !fg.getFilters().isEmpty()) {
            sb.append(" where ");
            buildConditions(sb, md, fg);
        }
        if (command.getOriginalWhere() != null) {
            sb.append("  and  (");
            if (!"1=1".equals(command.getOriginalWhere())) {
                if (md.getTableAlias() != null) {
                    sb.append(md.getTableAlias()).append(".");
                }
                sb.append(command.getOriginalWhere());
            } else {
                sb.append(command.getOriginalWhere());
            }
            sb.append("  )");
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
        // order by
        if (StringUtils.hasText(command.getOrderBy())) {
            sb.append(" order by ");
            sb.append(command.getOrderBy());
        }
        // limit offset count
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
        // fields
        buildSelectFields(sb, md, command);
        sb.append(" from ");
        sb.append(md.getTableName());
        // where
        FilterGroup fg = command.getWhere();
        if (fg != null && fg.getFilters() != null && !fg.getFilters().isEmpty()) {
            sb.append(" where ");
            buildConditions(sb, md, fg);
        }
        if (command.getOriginalWhere() != null) {
            sb.append("  and  (");
            if (!"1=1".equals(command.getOriginalWhere())) {
                if (md.getTableAlias() != null) {
                    sb.append(md.getTableAlias()).append(".");
                }
                sb.append(command.getOriginalWhere());
            } else {
                sb.append(command.getOriginalWhere());
            }
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
                FieldMeta fm = md.getFieldMeta(fieldName);
                if (command.getAlias().containsKey(fieldName)) {
                    // 有指定的重命名要求时
                    tryAppendKeywords(sb, fm.getColumnName());
                    sb.append(" ");
                    tryAppendKeywords(sb, command.getAlias().get(fieldName).toString());
                } else {
                    // 无指定的重命名要求，将数据库的字段格式转成实体字段格式，如role_id to roleId
                    if (fm.isEquals()) {
                        tryAppendKeywords(sb, fm.getColumnName());
                    } else {
                        tryAppendKeywords(sb, fm.getColumnName());
                        sb.append(" ");
                        tryAppendKeywords(sb, fm.getFieldName());
                    }
                }
            }
            sb.append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
    }

}
