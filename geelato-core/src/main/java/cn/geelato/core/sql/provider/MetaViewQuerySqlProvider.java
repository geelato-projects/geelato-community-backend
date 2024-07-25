package cn.geelato.core.sql.provider;

import cn.geelato.core.enums.ViewTypeEnum;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.QueryCommand;
import cn.geelato.core.gql.parser.QueryViewCommand;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.meta.model.view.ViewMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;


@Component
public class MetaViewQuerySqlProvider extends MetaBaseSqlProvider<QueryViewCommand> {
    private static Logger logger = LoggerFactory.getLogger(MetaViewQuerySqlProvider.class);

    @Override
    protected Object[] buildParams(QueryViewCommand command) {
        return buildWhereParams(command);
    }

    @Override
    protected int[] buildTypes(QueryViewCommand command) {
        return buildWhereTypes(command);
    }
    @Override
    protected String buildOneSql(QueryViewCommand command) {
        StringBuilder sb = new StringBuilder();
        EntityMeta md = getEntityMeta(command);
        sb.append("select  * ");
        sb.append(" from ");
        ViewMeta vm= md.getViewMeta(command.getViewName());
        if(vm.getViewType().equals(ViewTypeEnum.DEFAULT.getCode())){
            sb.append("(");
            sb.append(vm.getViewConstruct());
            sb.append(") as vt");
        }else {
            sb.append(vm.getViewName());
        }
        // where
        FilterGroup fg = command.getWhere();
        if (fg != null && fg.getFilters() != null && fg.getFilters().size() > 0) {
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
     * 构健统计数据
     *
     * @param command
     * @return
     */
    public String buildCountSql(QueryViewCommand command) {
        StringBuilder sb = new StringBuilder();
        EntityMeta md = getEntityMeta(command);
        sb.append("select count(*) from (");
        sb.append("select ");
        // fields
        buildSelectFields(sb, md, command.getFields(), command.getAlias());
        sb.append(" from ");
        sb.append(md.getTableName());
        //where
        FilterGroup fg = command.getWhere();
        if (fg != null && fg.getFilters() != null && fg.getFilters().size() > 0) {
            sb.append(" where ");
            buildConditions(sb, md, fg.getFilters(), fg.getLogic());
        }
        //group by
        if (StringUtils.hasText(command.getGroupBy())) {
            sb.append(" group by ");
            sb.append(command.getGroupBy());
        }
        //having
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
//            sb.append(md.getId().getColumnName());
            tryAppendKeywords(sb, md.getId().getColumnName());
        } else {
            FieldMeta fm = md.getFieldMeta(fields[0]);
            tryAppendKeywords(sb, fm.getColumnName());
//            sb.append(fm.getColumnName());
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
        //重命名查询的结果列表为实体字段名
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
