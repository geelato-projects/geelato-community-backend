package cn.geelato.core.sql.provider;

import cn.geelato.core.gql.command.QueryTreeCommand;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * @author geemeta
 */
@Component
public class MetaQueryTreeSqlProvider extends MetaBaseSqlProvider<QueryTreeCommand> {
    @Override
    protected Object[] buildParams(QueryTreeCommand command) {
        return buildWhereParams(command);
    }

    @Override
    protected int[] buildTypes(QueryTreeCommand command) {
        return buildWhereTypes(command);
    }

    @Override
    protected String buildOneSql(QueryTreeCommand command) {
        StringBuilder sb = new StringBuilder();
        EntityMeta md = getEntityMeta(command);
        sb.append("select tn.parent tn_parent, tn.icon tn_icon,tn.`type` tn_type,tn.tree_entity tn_tree_entity,tn.meta tn_meta,");
        buildSelectFields(sb, md, command.getFields(), command.getAlias());
        sb.append(" from platform_tree_node tn left join ");
        sb.append(md.getTableName());
        sb.append(" t on tn.id=t.tree_node_id ");
        // where
        FilterGroup fg = command.getWhere();
        if (fg != null && fg.getFilters() != null && !fg.getFilters().isEmpty()) {
            sb.append(" where ");
            buildConditions(sb, md, fg.getFilters(), fg.getLogic());
        }
        if (command.getOriginalWhere() != null) {
            sb.append("  and  (");
            if (!"1=1".equals(command.getOriginalWhere())) {
                sb.append(md.getTableAlias()).append(".").append(command.getOriginalWhere());
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
        // order by
        if (StringUtils.hasText(command.getOrderBy())) {
            sb.append(" order by ");
            sb.append(command.getOrderBy());
        }
        // limit offset count
        // TODO limit分页在数据记录达百万级时，性能较差
        if (command.isPagingQuery()) {
            sb.append(" limit ");
            sb.append((command.getPageNum() - 1) * command.getPageSize());
            sb.append(",");
            sb.append(command.getPageSize());
        }
        return sb.toString();
    }

    /**
     * 构健统计数据
     */
    public String buildCountSql(QueryTreeCommand command) {
        StringBuilder sb = new StringBuilder();
        EntityMeta md = getEntityMeta(command);
        sb.append("select count(*) from (");
        sb.append("select t.tree_node_id");
        // fields
        sb.append(" from platform_tree_node tn left join ");
        sb.append(md.getTableName());
        sb.append(" t on tn.id=t.tree_node_id ");
        // where
        FilterGroup fg = command.getWhere();
        if (fg != null && fg.getFilters() != null && !fg.getFilters().isEmpty()) {
            sb.append(" where ");
            buildConditions(sb, md, fg.getFilters(), fg.getLogic());
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
            tryAppendKeywords(sb, fm.getColumnName());
        }
    }

    private void buildSelectFields(StringBuilder sb, EntityMeta md, String[] fields, Map alias) {
        if (fields == null || fields.length == 0) {
            sb.append("*");
            return;
        } else if (fields.length == 1 && "*".equals(fields[0])) {
            sb.append("*");
            return;
        }
        // 重命名查询的结果列表为实体字段名
        for (String fieldName : fields) {
            FieldMeta fm = md.getFieldMeta(fieldName);
            if (alias.containsKey(fieldName)) {
                // 有指定的重命名要求时
                tryAppendKeywords(sb, fm.getColumnName());
                sb.append(" ");
                tryAppendKeywords(sb, alias.get(fieldName).toString());
            } else {
                if (fm.isEquals()) {
                    tryAppendKeywords(sb, fm.getColumnName());
                } else {
                    tryAppendKeywords(sb, fm.getColumnName());
                    sb.append(" ");
                    tryAppendKeywords(sb, fm.getFieldName());
                }
            }
            sb.append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
    }
}
