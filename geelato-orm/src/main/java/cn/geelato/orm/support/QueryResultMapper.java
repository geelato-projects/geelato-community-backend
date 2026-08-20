package cn.geelato.orm.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import org.springframework.util.ClassUtils;
import org.springframework.util.NumberUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.time.temporal.Temporal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 查询结果(Map 行)到目标类型的映射工具，供 fluent DSL 的 list()/one()/page() 使用。
 * <p>
 * 两类规则：
 * <ul>
 *     <li>自动解包：未显式指定类型时，结果集仅一列则直接返回该列裸值，多列仍返回行 Map；</li>
 *     <li>显式类型：Map 原样返回；简单类型要求单列并做值转换；其余(自定义 DTO/实体)经 JSON 映射，
 *     支持下划线列名到驼峰字段的智能匹配。</li>
 * </ul>
 */
public final class QueryResultMapper {

    private QueryResultMapper() {
    }

    /**
     * 自动解包：行仅一列时返回该列裸值，否则原样返回行。
     */
    @SuppressWarnings("unchecked")
    public static <R> R unwrapSingleColumn(Map<String, Object> row) {
        if (row != null && row.size() == 1) {
            return (R) row.values().iterator().next();
        }
        return (R) row;
    }

    /**
     * 自动解包：首行仅一列时把每行映射为该列裸值，否则原样返回行列表。
     */
    @SuppressWarnings("unchecked")
    public static <R> List<R> unwrapSingleColumn(List<Map<String, Object>> rows) {
        if (rows != null && !rows.isEmpty() && rows.get(0) != null && rows.get(0).size() == 1) {
            return (List<R>) rows.stream().map(row -> row.values().iterator().next()).collect(Collectors.toList());
        }
        return (List<R>) rows;
    }

    /**
     * 按目标类型映射单行。行为约定：行 Map 原样返回；简单类型要求单列并转换裸值；
     * 其他类型经 JSON 映射为对象实例；空行与 null 行返回 null。
     */
    public static <R> R mapRow(Map<String, Object> row, Class<R> type) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        if (Map.class.isAssignableFrom(type)) {
            return (R) row;
        }
        if (isSimpleType(type)) {
            if (row.size() != 1) {
                throw new IllegalArgumentException(
                        "Single-column result is required for type " + type.getName()
                                + " but got columns: " + String.join(", ", row.keySet()));
            }
            return convertValue(row.values().iterator().next(), type);
        }
        // SupportSmartMatch:支持下划线列名(email_address)到驼峰字段(emailAddress)的匹配
        return JSON.parseObject(JSON.toJSONString(row), type, JSONReader.Feature.SupportSmartMatch);
    }

    /**
     * 按目标类型逐行映射。
     */
    public static <R> List<R> mapRows(List<Map<String, Object>> rows, Class<R> type) {
        if (rows == null) {
            return null;
        }
        return rows.stream().map(row -> mapRow(row, type)).collect(Collectors.toList());
    }

    /**
     * wrapperResult 与显式类型组合时的兜底：结果已是目标类型直接返回，是 Map 则继续按类型映射。
     */
    @SuppressWarnings("unchecked")
    public static <R> R mapWrapped(Object value, Class<R> type) {
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (R) value;
        }
        if (value instanceof Map) {
            return mapRow((Map<String, Object>) value, type);
        }
        throw new IllegalStateException(
                "wrapperResult output (" + value.getClass().getName()
                        + ") is neither " + type.getName() + " nor a Map, cannot map to the required type");
    }

    /**
     * 简单类型：字符串、数值、布尔、字符、日期与 java.time 类型(含原子类型)。
     */
    public static boolean isSimpleType(Class<?> type) {
        if (type == null) {
            return false;
        }
        return type.isPrimitive()
                || type == String.class
                || type == Boolean.class
                || type == Character.class
                || Number.class.isAssignableFrom(type)
                || Date.class.isAssignableFrom(type)
                || Temporal.class.isAssignableFrom(type);
    }

    /**
     * 简单类型裸值转换：目标 String 用 String.valueOf；数值间互转或字符串解析为数值；
     * 布尔与字符支持字符串解析；无法转换时抛出 IllegalArgumentException。
     */
    @SuppressWarnings("unchecked")
    public static <R> R convertValue(Object value, Class<R> type) {
        if (value == null || type == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (R) value;
        }
        Class<R> targetType = (Class<R>) ClassUtils.resolvePrimitiveIfNecessary(type);
        if (targetType == String.class) {
            return (R) String.valueOf(value);
        }
        if (Number.class.isAssignableFrom(targetType) && (value instanceof Number || value instanceof String)) {
            try {
                Number number = value instanceof Number n ? n : NumberUtils.parseNumber((String) value, BigDecimal.class);
                return (R) NumberUtils.convertNumberToTargetClass(number, (Class<? extends Number>) targetType);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Unable to convert query column value '" + value + "' to " + type.getName(), e);
            }
        }
        if (targetType == Boolean.class && value instanceof String s) {
            return (R) Boolean.valueOf(s);
        }
        if (targetType == Character.class && value instanceof String s && s.length() == 1) {
            return (R) Character.valueOf(s.charAt(0));
        }
        throw new IllegalArgumentException(
                "Unable to convert query column value of " + value.getClass().getName() + " to " + type.getName());
    }
}
