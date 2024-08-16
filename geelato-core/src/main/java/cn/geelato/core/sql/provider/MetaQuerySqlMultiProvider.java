package cn.geelato.core.sql.provider;

import org.apache.logging.log4j.util.Strings;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.QueryCommand;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.TableForeign;
import cn.geelato.core.meta.model.field.ColumnMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 多表查询
 * @author liuwq
 */
@Component
public class MetaQuerySqlMultiProvider extends MetaBaseSqlProvider<QueryCommand> {
    private static final Logger logger = LoggerFactory.getLogger(MetaQuerySqlMultiProvider.class);

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
        //计算主表别名
        //md.setTableAlias(super.buildTableAlias(md.getTableName()));
        sb.append("select ");
        buildSelectFields(sb, md, command);
        sb.append(" from ");
        sb.append(command.getFrom());
        // where
        FilterGroup fg = command.getWhere();
        if (fg != null && fg.getFilters() != null && !fg.getFilters().isEmpty()) {
            sb.append(" where ");
            buildConditions(sb, md, fg);
        }
        if(command.getOriginalWhere()!=null){
            sb.append( "  and  ");
            if(!command.getOriginalWhere().equals("1=1")){
                if(md.getTableAlias()!=null) {
                    sb.append(md.getTableAlias()).append(".");
                }
                sb.append(command.getOriginalWhere());
            }else {
                sb.append(command.getOriginalWhere());
            }
        }
        // group by
        if (StringUtils.hasText(command.getGroupBy())) {
            sb.append(" group by ");
            sb.append(this.replaceTableAlias(command, command.getGroupBy()));
        }
        // having
        if (command.getHaving() != null) {
            sb.append(" having ");
            sb.append(command.getHaving());
        }
        // order by
        if (StringUtils.hasText(command.getOrderBy())) {
            sb.append(" order by ");
            sb.append(this.replaceTableAliasOrderBy(md, command.getOrderBy()));
        }
        // limit offset count
        // TODO limit分页在数据记录达百万级时，性能较差
        if (command.isPagingQuery()) {
            sb.append(" limit ");
            sb.append((command.getPageNum() - 1) * command.getPageSize());
            sb.append(",");
            sb.append(command.getPageSize());
        }

        command.setSelectSql(sb.toString());
        return command.getSelectSql();
    }

    /**
     * 构健统计数据
     *
     */
    public String buildCountSql(QueryCommand command) {
        StringBuilder sb = new StringBuilder();
        EntityMeta md = getEntityMeta(command);
        sb.append("select count(*) from (");
        String selectSql = command.getSelectSql();
        int seq = selectSql.indexOf("limit");
        if (seq != -1) {
            sb.append(selectSql, 0, seq);
        } else {
            sb.append(selectSql);
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

    private void buildSelectFields(StringBuilder sb, EntityMeta md, QueryCommand command) {
        //表别名
        command.appendFrom(md.getTableName(), md.getTableAlias());
        String[] fields = command.getFields();
        Map alias = command.getAlias();
        if (fields == null || fields.length == 0) {
            if(super.getTableAlias(md.getTableName())!=null){
                sb.append(super.getTableAlias(md.getTableName())).append(".*");
            }else{
                sb.append("*");
            }
            return;
        } else if (fields.length == 1 && "*".equals(fields[0])) {
            if (super.getTableAlias(md.getTableName()) != null) {
                sb.append(super.getTableAlias(md.getTableName())).append(".*");
            } else {
                sb.append("*");
            }
            return;
        }

        //重命名查询的结果列表为实体字段名
        for (String fieldName : fields) {
            FieldMeta fm = md.getFieldMeta(fieldName);
            ColumnMeta cm = fm.getColumn();
            //外表字段
            if (Strings.isNotEmpty(cm.getRefColName())) {
                if (!cm.getIsRefColumn()) {
                    //外键
                    this.buildForeignJoinSql(command, md, fm);
                } else if (Strings.isNotEmpty(cm.getRefLocalCol())) {
                    //非外键时，需要找到本表外键
                    FieldMeta localFm = md.getFieldMeta(cm.getRefLocalCol());
                    if (localFm != null) {
                        this.buildForeignJoinSql(command, md, localFm);
                    }
                }

                //外表字段
                String[] colArr = cm.getRefColName().split("\\.", 2);
                if (colArr.length == 2) {
                    sb.append(super.buildTableAlias(colArr[0])).append(".").append(colArr[1])
                            .append(" AS ").append(fieldName);
                }
            } else {
                if (alias.containsKey(fieldName)) {
                    tryAppendKeywords(md, sb, fm);
                    sb.append(" ");
                    tryAppendKeywords(sb, alias.get(fieldName).toString());
                } else {
                    if (!fm.getColumn().getIsRefColumn() && fm.isEquals()) {
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

    /**
     * 构建多表关联查询
     */
    private void buildForeignJoinSql(QueryCommand command, EntityMeta md, FieldMeta fm) {
        String[] fTables = fm.getColumn().getRefTables().split(",");
        for (int i = 0, len = fTables.length; i < len; i++) {
            String lastTable = i > 0 ? fTables[i-1] : md.getTableName();
            EntityMeta fEm = super.metaManager.get(fTables[i]);
            TableForeign tf = fEm.getTableForeignsMap().get(lastTable);
            //外键
            String tableAlias = super.buildTableAlias(fTables[i]);
            if (command.hasNotJoin(tableAlias)) {
                if (tf != null) {
                    //外键在fTables[i]表
                    command.appendFrom(" left join ").appendFrom(fTables[i], tableAlias)
                            .appendFrom(" on ").appendFrom(tableAlias).appendFrom(".").appendFrom(tf.getMainTableCol())
                            .appendFrom("=").appendFrom(super.getTableAlias(lastTable)).appendFrom(".").appendFrom(tf.getForeignTableCol());
                } else {
                    //外键在前一张表
                    fEm = super.metaManager.get(lastTable);
                    tf = fEm.getTableForeignsMap().get(fTables[i]);
                    if (tf != null) {
                        command.appendFrom(" left join ").appendFrom(fTables[i], tableAlias)
                                .appendFrom(" on ").appendFrom(tableAlias).appendFrom(".").appendFrom(tf.getForeignTableCol())
                                .appendFrom("=").appendFrom(super.getTableAlias(lastTable))
                                .appendFrom(".").appendFrom(tf.getMainTableCol());
                    }
                }

                //todo join中存多次引用同一张表场景待实现
            }
        }
    }

    /**
     * 替换SQL中表明为别名
     */
    private String replaceTableAlias(QueryCommand command, String sql) {
        StringBuilder newSql = new StringBuilder();
        if (sql != null) {
            String[] items = sql.split(".");
            if (items.length > 1) {
                for (String item : items) {
                    int seq = item.lastIndexOf(" ");
                    if (seq == -1) {
                        seq = 0;
                    } else {
                        newSql.append(item, 0, seq).append(" ");
                    }
                    String tableAlias = super.buildTableAlias(item.substring(seq));
                    if(tableAlias!=null){
                        newSql.append(tableAlias).append(".");
                    }

                }
            } else {
                newSql.append(sql);
            }
        }
        return newSql.toString();
    }

    /**
     * 替换SQL中表明为别名
     */
    private String replaceTableAliasOrderBy(EntityMeta md, String orderBySql) {
        StringBuilder newSql = new StringBuilder();
        if (orderBySql != null && !orderBySql.isEmpty()) {
            String[] items = orderBySql.split(",");
            for (int i = 0, len = items.length; i < len; i++) {
                if (!items[i].contains(".") &&md.getTableAlias()!=null) {
                    newSql.append(md.getTableAlias()).append(".");
                }
                newSql.append(items[i]);
                if (i < len - 1) {
                    newSql.append(",");
                }
            }
        }
        return newSql.toString();
    }

    @Override
    protected StringBuilder tryAppendKeywords(EntityMeta md, StringBuilder sb, FieldMeta fm) {
        String field = fm.getColumnName();
        if (fm.getColumn().getIsRefColumn()) {
            String fCol = fm.getColumn().getRefColName();
            String[] items = fCol.split("\\.");
            if (items.length == 2) {
                sb.append(super.buildTableAlias(items[0])).append(".");
                field = items[1];
            }
        } else {
            if(md.getTableAlias()!=null){
                sb.append(md.getTableAlias()).append(".");
            }
        }
        if (isKeywords(field)) {
            sb.append("'");
            sb.append(field);
            sb.append("'");
        } else {
            sb.append(field);
        }
        return sb;
    }
}
