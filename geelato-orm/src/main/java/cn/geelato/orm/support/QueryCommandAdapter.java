package cn.geelato.orm.support;

import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.orm.Order;
import cn.geelato.orm.query.MetaQuery;

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
        command.setFields(query.resolveSelectFields());
        command.setWhere(FilterAdapter.adapt(query.getFilters()));
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
        return command;
    }

    private static String toOrderBy(Order order) {
        return order.getField() + " " + order.getDirection();
    }
}
