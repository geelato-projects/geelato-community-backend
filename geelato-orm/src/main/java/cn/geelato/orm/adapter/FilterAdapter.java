package cn.geelato.orm.adapter;

import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.orm.query.Filter;

import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;
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
        filterGroup.addFilter(createFilter(first));
        while (iterator.hasNext()) {
            Filter current = iterator.next();
            filterGroup.addFilter(createFilter(current));
        }
        return filterGroup;
    }

    private static FilterGroup.Filter createFilter(Filter source) {
        FilterGroup.Filter fgFilter = new FilterGroup.Filter(
                source.getField(), mapOperator(source.getOperator()), normalizeValue(source));
        Object rawValue = source.getValue();
        if (rawValue != null && !(rawValue instanceof String) && !(rawValue instanceof Collection)) {
            fgFilter.setRawValue(rawValue);
        }
        return fgFilter;
    }

    /**
     * 核心 nil 操作符按 value 渲染（"1"/"true" → is NULL，否则 is NOT NULL），且 IS NULL 与
     * IS NOT NULL 映射到同一 Operator.nil，语义只由 value 区分——适配时统一规范化，
     * 裸构造 {@code new Filter(field, "IS NULL", null)} 缺省值也不会反转语义。
     */
    private static String normalizeValue(Filter filter) {
        String operator = filter.getOperator() == null ? "" : filter.getOperator().trim().toUpperCase(Locale.ROOT);
        if ("IS NULL".equals(operator)) {
            return "true";
        }
        if ("IS NOT NULL".equals(operator)) {
            return "false";
        }
        return stringifyValue(filter);
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
