package cn.geelato.orm;

import cn.geelato.orm.query.MetaQuery;
import cn.geelato.orm.query.MetaInsert;
import cn.geelato.orm.query.MetaUpdate;
import cn.geelato.orm.query.MetaDelete;

/**
 * 元数据操作工厂类
 * 提供流式API构建SQL查询、插入、更新、删除操作
 * 
 * 使用示例：
 * // 查询操作
 * MetaFactory.query("User")
 *     .select(new String[]{"id", "name", "email"})
 *     .where(Filter.eq("status", "active"))
 *     .order(Order.asc("name"))
 *     .page(1, 10)
 *     .toSql();
 * 
 * // 统计操作
 * MetaFactory.query("User")
 *     .where(Filter.eq("status", "active"))
 *     .count();
 * 
 * // 插入操作
 * MetaFactory.insert("User")
 *     .column(new String[]{"name", "email"})
 *     .values(new Object[]{"张三", "zhangsan@example.com"});
 * 
 * // 更新操作
 * MetaFactory.update("User")
 *     .column(new String[]{"name", "email"})
 *     .where(Filter.eq("id", 1));
 * 
 * // 删除操作
 * MetaFactory.delete("User")
 *     .where(Filter.eq("status", "inactive"));
 */
public class MetaFactory {
    
    /**
     * 创建查询构建器
     * @param entityName 实体名称
     * @return MetaQuery查询构建器
     */
    public static MetaQuery query(String entityName) {
        return new MetaQuery(entityName);
    }
    
    /**
     * 创建查询构建器
     * @param entityClass 实体类
     * @return MetaQuery查询构建器
     */
    public static MetaQuery query(Class<?> entityClass) {
        return new MetaQuery(entityClass.getSimpleName());
    }
    
    /**
     * 创建查询构建器（使用完整类名）
     * @param entityClass 实体类
     * @param useFullName 是否使用完整类名
     * @return MetaQuery查询构建器
     */
    public static MetaQuery query(Class<?> entityClass, boolean useFullName) {
        String entityName = useFullName ? entityClass.getName() : entityClass.getSimpleName();
        return new MetaQuery(entityName);
    }
    
    /**
     * 创建插入构建器
     * @param entityName 实体名称
     * @return MetaInsert插入构建器
     */
    public static MetaInsert insert(String entityName) {
        return new MetaInsert(entityName);
    }
    
    /**
     * 创建插入构建器
     * @param entityClass 实体类
     * @return MetaInsert插入构建器
     */
    public static MetaInsert insert(Class<?> entityClass) {
        return new MetaInsert(entityClass.getSimpleName());
    }
    
    /**
     * 创建更新构建器
     * @param entityName 实体名称
     * @return MetaUpdate更新构建器
     */
    public static MetaUpdate update(String entityName) {
        return new MetaUpdate(entityName);
    }
    
    /**
     * 创建更新构建器
     * @param entityClass 实体类
     * @return MetaUpdate更新构建器
     */
    public static MetaUpdate update(Class<?> entityClass) {
        return new MetaUpdate(entityClass.getSimpleName());
    }
    
    /**
     * 创建删除构建器
     * @param entityName 实体名称
     * @return MetaDelete删除构建器
     */
    public static MetaDelete delete(String entityName) {
        return new MetaDelete(entityName);
    }
    
    /**
     * 创建删除构建器
     * @param entityClass 实体类
     * @return MetaDelete删除构建器
     */
    public static MetaDelete delete(Class<?> entityClass) {
        return new MetaDelete(entityClass.getSimpleName());
    }
}