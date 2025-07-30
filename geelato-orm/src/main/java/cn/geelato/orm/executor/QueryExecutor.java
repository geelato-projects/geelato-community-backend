package cn.geelato.orm.executor;

import cn.geelato.orm.query.MetaQuery;
import jakarta.annotation.Resource;

import java.util.List;
import java.util.Map;

/**
 * 查询执行器接口
 * 定义SQL执行的标准接口
 */
public interface QueryExecutor {
    
    /**
     * 执行查询，返回结果列表
     * @param query MetaQuery查询对象
     * @return 查询结果列表
     */
    List<Map<String, Object>> executeQuery(MetaQuery query);
    
    /**
     * 执行查询，返回单个结果
     * @param query MetaQuery查询对象
     * @return 单个查询结果
     */
    Map<String, Object> executeQueryForObject(MetaQuery query);
    
    /**
     * 执行统计查询，返回总数
     * @param query MetaQuery查询对象
     * @return 查询结果总数
     */
    long executeCount(MetaQuery query);
    
    /**
     * 执行分页查询，返回分页结果
     * @param query MetaQuery查询对象
     * @return 分页查询结果
     */
    PageResult<Map<String, Object>> executePageQuery(MetaQuery query);
    
    /**
     * 直接执行SQL语句
     * @param sql SQL语句
     * @return 查询结果列表
     */
    List<Map<String, Object>> executeSql(String sql);
    
    /**
     * 执行查询并使用包装函数处理结果
     * @param query MetaQuery查询对象
     * @param <R> 返回类型
     * @return 包装后的查询结果列表
     */
    <R> List<R> executeQueryWithWrapper(MetaQuery query);
    
    /**
     * 执行查询并使用包装函数处理单个结果
     * @param query MetaQuery查询对象
     * @param <R> 返回类型
     * @return 包装后的单个查询结果
     */
    <R> R executeQueryForObjectWithWrapper(MetaQuery query);
    
    /**
     * 执行分页查询并使用包装函数处理结果
     * @param query MetaQuery查询对象
     * @param <R> 返回类型
     * @return 包装后的分页查询结果
     */
    <R> PageResult<R> executePageQueryWithWrapper(MetaQuery query);
}