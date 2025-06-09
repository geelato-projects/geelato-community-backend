package cn.geelato.orm;

import lombok.Getter;

/**
 * 排序规则类
 * 用于构建SQL的ORDER BY条件
 */
@Getter
public class Order {
    // Getters
    private final String field;
    private final String direction;
    
    public Order(String field, String direction) {
        this.field = field;
        this.direction = direction;
    }
    
    /**
     * 升序排序
     * @param field 字段名
     * @return Order对象
     */
    public static Order asc(String field) {
        return new Order(field, "ASC");
    }
    
    /**
     * 降序排序
     * @param field 字段名
     * @return Order对象
     */
    public static Order desc(String field) {
        return new Order(field, "DESC");
    }

    /**
     * 转换为SQL片段
     */
    public String toSql() {
        return field + " " + direction;
    }
}