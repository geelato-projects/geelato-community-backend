package cn.geelato.orm;

import lombok.Getter;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 排序元素载体。
 * 从 MyBatis-Plus OrderItem 迁移而来，保留当前 ORM 所需的兼容接口。
 */
@Getter
public class OrderItem implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 需要进行排序的字段。
     */
    private String column;

    /**
     * 是否正序排列，默认 true。
     */
    private boolean asc = true;

    public OrderItem() {
    }

    private OrderItem(String column, boolean asc) {
        this.column = column;
        this.asc = asc;
    }

    public static OrderItem asc(String column) {
        return build(column, true);
    }

    public static OrderItem desc(String column) {
        return build(column, false);
    }

    public static OrderItem withExpression(String expression) {
        return withExpression(expression, false);
    }

    public static OrderItem withExpression(String expression, boolean asc) {
        return new OrderItem(expression, asc);
    }

    public static List<OrderItem> ascs(String... columns) {
        return Arrays.stream(columns).map(OrderItem::asc).collect(Collectors.toList());
    }

    public static List<OrderItem> descs(String... columns) {
        return Arrays.stream(columns).map(OrderItem::desc).collect(Collectors.toList());
    }

    private static OrderItem build(String column, boolean asc) {
        return new OrderItem().setColumn(column).setAsc(asc);
    }

    public OrderItem setColumn(String column) {
        this.column = column == null ? null : column.replaceAll("\\s+", "");
        return this;
    }

    public OrderItem setAsc(boolean asc) {
        this.asc = asc;
        return this;
    }

    @Override
    public String toString() {
        return "OrderItem{" +
                "column='" + column + '\'' +
                ", asc=" + asc +
                '}';
    }
}
