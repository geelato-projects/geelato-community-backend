package cn.geelato.orm.support;

import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.orm.Filter;

import java.util.Collection;
import java.util.Iterator;
import java.util.stream.Collectors;

/**
 * Fluent DSL 过滤条件到核心 FilterGroup 的适配器。
 */
public final class FilterAdapter {

    private FilterAdapter() {
    }

    public static FilterGroup adapt(Collection<Filter> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        FilterGroup filterGroup = new FilterGroup();
        Iterator<Filter> iterator = filters.iterator();
        Filter first = iterator.next();
        filterGroup.setLogic("OR".equalsIgnoreCase(first.getLogic()) ? FilterGroup.Logic.or : FilterGroup.Logic.and);
        filterGroup.addFilter(new FilterGroup.Filter(first.getField(), mapOperator(first.getOperator()), stringifyValue(first)));
        while (iterator.hasNext()) {
            Filter current = iterator.next();
            filterGroup.addFilter(new FilterGroup.Filter(current.getField(), mapOperator(current.getOperator()), stringifyValue(current)));
        }
        return filterGroup;
    }

    private static FilterGroup.Operator mapOperator(String operator) {
        return switch (operator.toUpperCase()) {
            case "=" -> FilterGroup.Operator.eq;
            case "!=", "<>" -> FilterGroup.Operator.neq;
            case ">" -> FilterGroup.Operator.gt;
            case ">=" -> FilterGroup.Operator.gte;
            case "<" -> FilterGroup.Operator.lt;
            case "<=" -> FilterGroup.Operator.lte;
            case "LIKE" -> FilterGroup.Operator.contains;
            case "IN" -> FilterGroup.Operator.in;
            case "IS NULL", "IS NOT NULL" -> FilterGroup.Operator.nil;
            default -> throw new IllegalArgumentException("不支持的过滤操作符: " + operator);
        };
    }

    private static String stringifyValue(Filter filter) {
        Object value = filter.getValue();
        if (value == null) {
            return "";
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).collect(Collectors.joining(","));
        }
        return String.valueOf(value);
    }
}
