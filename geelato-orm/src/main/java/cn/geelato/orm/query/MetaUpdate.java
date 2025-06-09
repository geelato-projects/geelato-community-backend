package cn.geelato.orm.query;

import cn.geelato.orm.Filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

/**
 * 元数据更新构建器
 * 提供流式API构建SQL更新语句
 */
public class MetaUpdate extends MetaOperate {
    private String[] columns;
    private Object[] values;

    
    public MetaUpdate(String entityName) {
        this.entityName = entityName;
    }
    
    /**
     * 设置更新的字段
     * @param columns 字段数组
     * @return MetaUpdate对象，支持链式调用
     */
    public MetaUpdate column(String[] columns) {
        this.columns = columns;
        return this;
    }
    
    /**
     * 设置更新的值
     * @param values 值数组
     * @return MetaUpdate对象，支持链式调用
     */
    public MetaUpdate values(Object[] values) {
        this.values = values;
        return this;
    }
    
    /**
     * 添加过滤条件
     * @param filter 过滤条件
     * @return MetaUpdate对象，支持链式调用
     */
    public MetaUpdate where(Filter filter) {
        this.filters.add(filter);
        return this;
    }
    
    /**
     * 添加多个过滤条件
     * @param filters 过滤条件数组
     * @return MetaUpdate对象，支持链式调用
     */
    public MetaUpdate where(Filter... filters) {
        this.filters.addAll(Arrays.asList(filters));
        return this;
    }
    
    /**
     * 构建更新SQL语句
     * @return 完整的SQL更新语句
     */
    public String toSql() {
        StringBuilder sql = new StringBuilder();
        
        // UPDATE子句
        sql.append("UPDATE ").append(entityName);
        
        // SET子句
        if (columns != null && columns.length > 0) {
            sql.append(" SET ");
            StringJoiner setJoiner = new StringJoiner(", ");
            for (int i = 0; i < columns.length; i++) {
                String column = columns[i];
                Object value = (values != null && i < values.length) ? values[i] : null;
                
                if (value instanceof String) {
                    setJoiner.add(column + " = '" + value + "'");
                } else {
                    setJoiner.add(column + " = " + value);
                }
            }
            sql.append(setJoiner.toString());
        }
        
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
     * 执行更新操作，返回影响行数
     * @return 影响行数
     */
    public int execute() {
        // 这里应该调用实际的数据库执行器来执行更新操作
        // 为了演示，这里返回一个模拟值
        String updateSql = toSql();
        System.out.println("执行更新SQL: " + updateSql);
        
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