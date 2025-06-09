package cn.geelato.orm;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * 过滤条件类
 * 用于构建SQL的WHERE条件
 */
@Getter
public class Filter {
    // Getters
    private String field;
    private String operator;
    private Object value;
    private String logic = "AND"; // 默认AND连接
    
    public Filter(String field, String operator, Object value) {
        this.field = field;
        this.operator = operator;
        this.value = value;
    }
    
    public Filter(String field, String operator, Object value, String logic) {
        this.field = field;
        this.operator = operator;
        this.value = value;
        this.logic = logic;
    }
    
    // 等于
    public static Filter eq(String field, Object value) {
        return new Filter(field, "=", value);
    }
    
    // 不等于
    public static Filter ne(String field, Object value) {
        return new Filter(field, "!=", value);
    }
    
    // 大于
    public static Filter gt(String field, Object value) {
        return new Filter(field, ">", value);
    }
    
    // 大于等于
    public static Filter ge(String field, Object value) {
        return new Filter(field, ">=", value);
    }
    
    // 小于
    public static Filter lt(String field, Object value) {
        return new Filter(field, "<", value);
    }
    
    // 小于等于
    public static Filter le(String field, Object value) {
        return new Filter(field, "<=", value);
    }
    
    // LIKE模糊查询
    public static Filter like(String field, String value) {
        return new Filter(field, "LIKE", "%" + value + "%");
    }
    
    // IN查询
    public static Filter in(String field, Object... values) {
        return new Filter(field, "IN", Arrays.asList(values));
    }
    
    // IS NULL
    public static Filter isNull(String field) {
        return new Filter(field, "IS NULL", null);
    }
    
    // IS NOT NULL
    public static Filter isNotNull(String field) {
        return new Filter(field, "IS NOT NULL", null);
    }
    
    // OR连接
    public Filter or() {
        this.logic = "OR";
        return this;
    }
    
    // AND连接
    public Filter and() {
        this.logic = "AND";
        return this;
    }

    /**
     * 转换为SQL片段
     */
    public String toSql() {
        StringBuilder sql = new StringBuilder();
        sql.append(field).append(" ").append(operator);
        
        if (value != null) {
            if ("IN".equals(operator) && value instanceof List) {
                List<?> list = (List<?>) value;
                sql.append(" (");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sql.append(", ");
                    sql.append(formatValue(list.get(i)));
                }
                sql.append(")");
            } else {
                sql.append(" ").append(formatValue(value));
            }
        }
        
        return sql.toString();
    }
    
    private String formatValue(Object val) {
        if (val == null) {
            return "NULL";
        }
        if (val instanceof String) {
            return "'" + val.toString().replace("'", "''") + "'";
        }
        return val.toString();
    }
}