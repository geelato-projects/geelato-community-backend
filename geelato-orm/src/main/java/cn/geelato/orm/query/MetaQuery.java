package cn.geelato.orm.query;

import cn.geelato.orm.Filter;
import cn.geelato.orm.WrapperResultFunction;
import cn.geelato.orm.Order;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

/**
 * 元数据查询构建器
 * 提供流式API构建SQL查询
 */
@Getter
public class MetaQuery extends MetaOperate {
    private String[] selectFields;
    private List<Order> orders = new ArrayList<>();
    private Integer pageNum;
    private Integer pageSize;
    private WrapperResultFunction<?, ?> wrapperFunction;
    
    public MetaQuery(String entityName) {
        this.entityName = entityName;
    }
    
    /**
     * 设置查询字段
     * @param fields 字段数组
     * @return MetaQuery对象，支持链式调用
     */
    public MetaQuery select(String[] fields) {
        this.selectFields = fields;
        return this;
    }
    
    /**
     * 添加单个过滤条件
     * @param filter 过滤条件
     * @return MetaQuery对象，支持链式调用
     */
    public MetaQuery where(Filter filter) {
        this.filters.add(filter);
        return this;
    }
    
    /**
     * 添加多个过滤条件
     * @param filters 过滤条件数组
     * @return MetaQuery对象，支持链式调用
     */
    public MetaQuery where(Filter... filters) {
        this.filters.addAll(Arrays.asList(filters));
        return this;
    }
    
    /**
     * 添加单个排序规则
     * @param order 排序规则
     * @return MetaQuery对象，支持链式调用
     */
    public MetaQuery order(Order order) {
        this.orders.add(order);
        return this;
    }
    
    /**
     * 添加多个排序规则
     * @param orders 排序规则数组
     * @return MetaQuery对象，支持链式调用
     */
    public MetaQuery order(Order... orders) {
        this.orders.addAll(Arrays.asList(orders));
        return this;
    }
    
    /**
     * 设置分页参数
     * @param pageNum 页码（从1开始）
     * @param pageSize 每页数量
     * @return MetaQuery对象，支持链式调用
     */
    public MetaQuery page(int pageNum, int pageSize) {
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        return this;
    }
    
    /**
     * 设置结果包装函数
     * @param wrapperFunction 结果包装函数
     * @param <T> 输入类型
     * @param <R> 返回类型
     * @return MetaQuery对象，支持链式调用
     */
    public <T, R> MetaQuery wrapperResult(WrapperResultFunction<T, R> wrapperFunction) {
        this.wrapperFunction = wrapperFunction;
        return this;
    }
    
    /**
     * 构建SQL语句
     * @return 完整的SQL查询语句
     */
    public String toSql() {
        StringBuilder sql = new StringBuilder();
        
        // SELECT子句
        sql.append("SELECT ");
        if (selectFields != null && selectFields.length > 0) {
            sql.append(String.join(", ", selectFields));
        } else {
            sql.append("*");
        }
        
        // FROM子句
        sql.append(" FROM ").append(entityName);
        
        // WHERE子句
        if (!filters.isEmpty()) {
            sql.append(" WHERE ");
            StringJoiner whereJoiner = new StringJoiner(" ");
            for (int i = 0; i < filters.size(); i++) {
                Filter filter = filters.get(i);
                if (i > 0) {
                    whereJoiner.add(filter.getLogic());
                }
                whereJoiner.add(filter.toSql());
            }
            sql.append(whereJoiner.toString());
        }
        
        // ORDER BY子句
        if (!orders.isEmpty()) {
            sql.append(" ORDER BY ");
            StringJoiner orderJoiner = new StringJoiner(", ");
            for (Order order : orders) {
                orderJoiner.add(order.toSql());
            }
            sql.append(orderJoiner.toString());
        }
        
        // LIMIT子句（分页）
        if (pageNum != null && pageSize != null) {
            int offset = (pageNum - 1) * pageSize;
            sql.append(" LIMIT ").append(pageSize).append(" OFFSET ").append(offset);
        }
        
        return sql.toString();
    }
    
    /**
     * 获取查询结果总数的SQL
     * @return 统计总数的SQL语句
     */
    public String toCountSql() {
        StringBuilder sql = new StringBuilder();
        
        // SELECT COUNT(*)
        sql.append("SELECT COUNT(*)");
        
        // FROM子句
        sql.append(" FROM ").append(entityName);
        
        // WHERE子句
        if (!filters.isEmpty()) {
            sql.append(" WHERE ");
            StringJoiner whereJoiner = new StringJoiner(" ");
            for (int i = 0; i < filters.size(); i++) {
                Filter filter = filters.get(i);
                if (i > 0) {
                    whereJoiner.add(filter.getLogic());
                }
                whereJoiner.add(filter.toSql());
            }
            sql.append(whereJoiner.toString());
        }
        
        return sql.toString();
    }
    
    /**
     * 执行统计查询，返回统计行数
     * @return 统计行数
     */
    public long count() {
        // 这里应该调用实际的数据库执行器来执行统计查询
        // 为了演示，这里返回一个模拟值
        // 在实际实现中，应该通过QueryExecutor来执行toCountSql()并返回结果
        String countSql = toCountSql();
        System.out.println("执行统计SQL: " + countSql);
        
        // 模拟返回统计结果
        return 100L; // 实际应该执行SQL并返回真实结果
    }

    // Getters
    public String getEntityName() {
        return entityName;
    }

    public List<Filter> getFilters() {
        return filters;
    }

}