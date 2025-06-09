package cn.geelato.orm.query;

import cn.geelato.orm.Filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

/**
 * 元数据插入构建器
 * 提供流式API构建SQL插入语句
 */
public class MetaInsert extends MetaOperate {
    private String[] columns;
    private Object[] values;
    public MetaInsert(String entityName) {
        this.entityName = entityName;
    }
    
    /**
     * 设置插入的字段
     * @param columns 字段数组
     * @return MetaInsert对象，支持链式调用
     */
    public MetaInsert column(String[] columns) {
        this.columns = columns;
        return this;
    }
    
    /**
     * 设置插入的值
     * @param values 值数组
     * @return MetaInsert对象，支持链式调用
     */
    public MetaInsert values(Object[] values) {
        this.values = values;
        return this;
    }
    
    /**
     * 添加过滤条件（用于INSERT ... SELECT语句）
     * @param filter 过滤条件
     * @return MetaInsert对象，支持链式调用
     */
    public MetaInsert where(Filter filter) {
        this.filters.add(filter);
        return this;
    }
    
    /**
     * 添加多个过滤条件
     * @param filters 过滤条件数组
     * @return MetaInsert对象，支持链式调用
     */
    public MetaInsert where(Filter... filters) {
        this.filters.addAll(Arrays.asList(filters));
        return this;
    }
    
    /**
     * 构建插入SQL语句
     * @return 完整的SQL插入语句
     */
    public String toSql() {
        StringBuilder sql = new StringBuilder();
        
        // INSERT INTO子句
        sql.append("INSERT INTO ").append(entityName);
        
        // 字段列表
        if (columns != null && columns.length > 0) {
            sql.append(" (").append(String.join(", ", columns)).append(")");
        }
        
        // VALUES子句
        if (values != null && values.length > 0) {
            sql.append(" VALUES (");
            StringJoiner valueJoiner = new StringJoiner(", ");
            for (Object value : values) {
                if (value instanceof String) {
                    valueJoiner.add("'" + value + "'");
                } else {
                    valueJoiner.add(String.valueOf(value));
                }
            }
            sql.append(valueJoiner.toString()).append(")");
        }
        
        return sql.toString();
    }
    
    /**
     * 执行插入操作，返回影响行数
     * @return 影响行数
     */
    public int execute() {
        // 这里应该调用实际的数据库执行器来执行插入操作
        // 为了演示，这里返回一个模拟值
        String insertSql = toSql();
        System.out.println("执行插入SQL: " + insertSql);
        
        // 模拟返回影响行数
        return 1; // 实际应该执行SQL并返回真实结果
    }
    
    // Getters
    public String getEntityName() {
        return entityName;
    }
    
    public String[] getColumns() {
        return columns;
    }
    
    public Object[] getValues() {
        return values;
    }
    
    public List<Filter> getFilters() {
        return filters;
    }
}