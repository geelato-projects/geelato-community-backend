package cn.geelato.orm.query;

import cn.geelato.orm.Filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

/**
 * 元数据删除构建器
 * 提供流式API构建SQL删除语句
 */
public class MetaDelete extends MetaOperate {

    public MetaDelete(String entityName) {
        this.entityName = entityName;
    }
    
    /**
     * 添加过滤条件
     * @param filter 过滤条件
     * @return MetaDelete对象，支持链式调用
     */
    public MetaDelete where(Filter filter) {
        this.filters.add(filter);
        return this;
    }
    
    /**
     * 添加多个过滤条件
     * @param filters 过滤条件数组
     * @return MetaDelete对象，支持链式调用
     */
    public MetaDelete where(Filter... filters) {
        this.filters.addAll(Arrays.asList(filters));
        return this;
    }
    
    /**
     * 构建删除SQL语句
     * @return 完整的SQL删除语句
     */
    public String toSql() {
        StringBuilder sql = new StringBuilder();
        
        // DELETE FROM子句
        sql.append("DELETE FROM ").append(entityName);
        
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
     * 执行删除操作，返回影响行数
     * @return 影响行数
     */
    public int execute() {
        // 这里应该调用实际的数据库执行器来执行删除操作
        // 为了演示，这里返回一个模拟值
        String deleteSql = toSql();
        System.out.println("执行删除SQL: " + deleteSql);
        
        // 模拟返回影响行数
        return 1; // 实际应该执行SQL并返回真实结果
    }
    
    // Getters
    public String getEntityName() {
        return entityName;
    }
    
    public List<Filter> getFilters() {
        return filters;
    }
}