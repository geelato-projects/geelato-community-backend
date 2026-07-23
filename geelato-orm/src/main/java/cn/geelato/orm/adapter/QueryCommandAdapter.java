package cn.geelato.orm.adapter;

import cn.geelato.core.mql.command.QueryJoin;
import cn.geelato.core.mql.command.QueryJoinCondition;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.command.QuerySelectExpr;
import cn.geelato.orm.query.Order;
import cn.geelato.orm.query.JoinClause;
import cn.geelato.orm.query.JoinCondition;
import cn.geelato.orm.query.MetaQuery;
import cn.geelato.orm.spi.support.FluentQueryFilterRuntimeResolver;

import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * MetaQuery 到 QueryCommand 的适配器。
 */
public final class QueryCommandAdapter {

    private QueryCommandAdapter() {
    }

    public static QueryCommand forList(MetaQuery query) {
        QueryCommand command = adapt(query);
        command.setQueryForList(true);
        return command;
    }

    public static QueryCommand forObject(MetaQuery query) {
        QueryCommand command = adapt(query);
        command.setQueryForList(false);
        return command;
    }

    private static QueryCommand adapt(MetaQuery query) {
        QueryCommand command = new QueryCommand();
        command.setEntityName(query.resolveEntityName());
        command.setConnectId(query.getConnectId());
        command.setFields(query.resolveSelectFields());
        command.setWhere(FilterAdapter.adapt(query.getFilters()));
        command.setTableAlias(query.getTableAlias());
        command.getAlias().putAll(query.getSelectAliases());
        if (!query.getRefFields().isEmpty()) {
            command.setForeignFields(query.getRefFields().toArray(new String[0]));
        }
        if (!query.getSelectExprs().isEmpty()) {
            command.setSelectExprs(query.getSelectExprs().stream()
                    .map(expr -> new QuerySelectExpr(expr.getExpression(), expr.getAlias()))
                    .collect(Collectors.toCollection(ArrayList::new)));
        }
        if (!query.getJoins().isEmpty()) {
            command.setJoins(query.getJoins().stream()
                    .map(QueryCommandAdapter::toQueryJoin)
                    .collect(Collectors.toCollection(ArrayList::new)));
        }
        if (query.getGroupByFields() != null && query.getGroupByFields().length > 0) {
            command.setGroupBy(String.join(",", query.getGroupByFields()));
        }
        if (query.getHavingSql() != null && !query.getHavingSql().isBlank()) {
            command.setHavingSql(query.getHavingSql());
        }
        if (!query.getOrders().isEmpty()) {
            String orderBy = query.getOrders().stream()
                    .map(QueryCommandAdapter::toOrderBy)
                    .collect(Collectors.joining(","));
            command.setOrderBy(orderBy);
        }
        if (query.getPageNum() != null && query.getPageNum() > 0 && query.getPageSize() != null && query.getPageSize() > 0) {
            command.setPageNum(query.getPageNum());
            command.setPageSize(query.getPageSize());
        }
        if (!query.getViewTemplateParams().isEmpty()) {
            command.setViewTemplateParams(query.getViewTemplateParams());
        }
        FluentQueryFilterRuntimeResolver.injectIfAvailable(command, query);
        return command;
    }

    private static String toOrderBy(Order order) {
        return order.getField() + " " + order.getDirection();
    }

    private static QueryJoin toQueryJoin(JoinClause joinClause) {
        QueryJoin queryJoin = new QueryJoin();
        queryJoin.setEntityName(joinClause.getEntityName());
        queryJoin.setAlias(joinClause.getAlias());
        queryJoin.setJoinType(joinClause.getJoinType().getSqlKeyword());
        queryJoin.setConditions(joinClause.getConditions().stream()
                .map(QueryCommandAdapter::toQueryJoinCondition)
                .collect(Collectors.toCollection(ArrayList::new)));
        return queryJoin;
    }

    private static QueryJoinCondition toQueryJoinCondition(JoinCondition condition) {
        QueryJoinCondition queryJoinCondition = new QueryJoinCondition();
        queryJoinCondition.setLeftField(condition.getLeftField());
        queryJoinCondition.setOperator(condition.getOperator());
        queryJoinCondition.setRightField(condition.getRightField());
        queryJoinCondition.setRawExpression(condition.getRawExpression());
        return queryJoinCondition;
    }
}
