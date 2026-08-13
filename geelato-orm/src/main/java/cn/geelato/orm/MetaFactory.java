package cn.geelato.orm;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.orm.query.MetaQuery;
import cn.geelato.orm.query.MetaInsert;
import cn.geelato.orm.query.MetaUpdate;
import cn.geelato.orm.query.MetaDelete;
import cn.geelato.orm.query.MetaNativeSql;
import cn.geelato.orm.query.MetaProcedure;
import cn.geelato.orm.query.Filter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.PropertyUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 元数据操作工厂类
 * 提供流式API构建SQL查询、插入、更新、删除操作
 * <p>
 * 使用示例：
 * // 查询操作
 * MetaFactory.query("User")
 *     .select(new String[]{"id", "name", "email"})
 *     .where(Filter.eq("status", "active"))
 *     .order(Order.asc("name"))
 *     .page(1, 10)
 *     .toSql();
 * <p>
 * // 统计操作
 * MetaFactory.query("User")
 *     .where(Filter.eq("status", "active"))
 *     .count();
 * <p>
 * // 插入操作
 * MetaFactory.insert("User")
 *     .column(new String[]{"name", "email"})
 *     .values(new Object[]{"张三", "zhangsan@example.com"});
 * <p>
 * // 更新操作
 * MetaFactory.update("User")
 *     .column(new String[]{"name", "email"})
 *     .where(Filter.eq("id", 1));
 * <p>
 * // 删除操作
 * MetaFactory.delete("User")
 *     .where(Filter.eq("status", "inactive"));
 */
@Slf4j
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
        return new MetaQuery(entityClass);
    }
    
    /**
     * 创建查询构建器（使用完整类名）
     * @param entityClass 实体类
     * @param useFullName 是否使用完整类名
     * @return MetaQuery查询构建器
     */
    public static MetaQuery query(Class<?> entityClass, boolean useFullName) {
        return useFullName ? new MetaQuery(entityClass.getName()) : new MetaQuery(entityClass);
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
        return new MetaInsert(entityClass);
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
        return new MetaUpdate(entityClass);
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
        return new MetaDelete(entityClass);
    }

    /**
     * 创建存储过程执行构建器
     * @param procedureName 存储过程名称
     * @return MetaProcedure存储过程执行构建器
     */
    public static MetaProcedure procedure(String procedureName) {
        return new MetaProcedure(procedureName);
    }

    /**
     * 创建原生 SQL 直通执行构建器
     * @param sql 完整 SQL 语句
     * @return MetaNativeSql 原生 SQL 执行构建器
     */
    public static MetaNativeSql sql(String sql) {
        return new MetaNativeSql(sql);
    }

    // ==================== 基于实体对象的重载 ====================

    /**
     * 按实体对象的非空属性构建查询（按属性等值查询）。
     * <p>
     * 使用示例：
     * // 按属性查询（非空属性作为等值条件）
     * MetaFactory.query(userExample).list();
     * @param entity 实体对象，非空属性作为等值查询条件
     * @return MetaQuery查询构建器，可继续链式调用
     */
    public static MetaQuery query(Object entity) {
        MetaQuery query = new MetaQuery(entity.getClass());
        for (Filter filter : extractFilters(entity)) {
            query.where(filter);
        }
        return query;
    }

    /**
     * 按实体对象的非空属性构建插入构建器。
     * <p>
     * 使用示例：
     * // 插入（可链式切换数据源）
     * MetaFactory.insert(entity).useDataSource("biz").save();
     * @param entity 实体对象，非空属性作为插入字段
     * @return MetaInsert插入构建器，链式调用 .save() 执行
     */
    public static MetaInsert insert(Object entity) {
        return new MetaInsert(entity.getClass()).values(extractValues(entity));
    }

    /**
     * 按实体对象的非空属性构建更新构建器。
     * 若实体对象主键非空，自动追加 where(主键) 条件。
     * <p>
     * 使用示例：
     * // 更新（可链式切换数据源）
     * MetaFactory.update(entity).useDataSource("biz").save();
     * @param entity 实体对象，非空属性作为更新字段
     * @return MetaUpdate更新构建器，链式调用 .save() 执行
     */
    public static MetaUpdate update(Object entity) {
        MetaUpdate update = new MetaUpdate(entity.getClass()).values(extractValues(entity));
        EntityMeta entityMeta = metaOf(entity);
        Object id = idValue(entity, entityMeta);
        if (hasId(id)) {
            update.where(Filter.eq(entityMeta.getId().getFieldName(), id));
        }
        return update;
    }

    /**
     * 按实体对象的非空属性构建删除构建器（等值条件）。
     * <p>
     * 使用示例：
     * // 删除（可链式切换数据源）
     * MetaFactory.delete(entity).useDataSource("biz").delete();
     * @param entity 实体对象，非空属性作为等值删除条件
     * @return MetaDelete删除构建器，链式调用 .delete() 执行
     */
    public static MetaDelete delete(Object entity) {
        MetaDelete delete = new MetaDelete(entity.getClass());
        for (Filter filter : extractFilters(entity)) {
            delete.where(filter);
        }
        return delete;
    }

    /**
     * 直接保存实体对象：主键为空执行插入，主键非空执行按主键更新。
     * <p>
     * 使用示例：
     * // 一行执行：新增（主键空→插入，自动生成主键）
     * String id = MetaFactory.save(new Meta());
     * // 一行执行：更新（主键非空→按主键更新）
     * entity.setId(id);
     * MetaFactory.save(entity);
     * @param entity 实体对象
     * @return 保存后的主键值
     */
    public static String save(Object entity) {
        return hasId(idValue(entity, metaOf(entity)))
                ? update(entity).save()
                : insert(entity).save();
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 获取实体对象对应的实体元数据。
     */
    private static EntityMeta metaOf(Object entity) {
        return MetaManager.singleInstance().get(entity.getClass());
    }

    /**
     * 提取实体对象非空映射属性的值映射（字段名 -> 值），跳过 null。
     */
    private static Map<String, Object> extractValues(Object entity) {
        EntityMeta entityMeta = metaOf(entity);
        Map<String, Object> values = new LinkedHashMap<>();
        for (FieldMeta fieldMeta : entityMeta.getFieldMetas()) {
            String fieldName = fieldMeta.getFieldName();
            Object value = readProperty(entity, fieldName);
            if (value != null) {
                values.put(fieldName, value);
            }
        }
        return values;
    }

    /**
     * 提取实体对象非空映射属性的等值过滤条件列表，跳过 null。
     */
    private static List<Filter> extractFilters(Object entity) {
        EntityMeta entityMeta = metaOf(entity);
        List<Filter> filters = new ArrayList<>();
        for (FieldMeta fieldMeta : entityMeta.getFieldMetas()) {
            String fieldName = fieldMeta.getFieldName();
            Object value = readProperty(entity, fieldName);
            if (value != null) {
                filters.add(Filter.eq(fieldName, value));
            }
        }
        return filters;
    }

    /**
     * 读取实体对象的主键值。
     */
    private static Object idValue(Object entity, EntityMeta entityMeta) {
        return readProperty(entity, entityMeta.getId().getFieldName());
    }

    /**
     * 判断主键值是否存在（非 null，且非空白字符串）。
     */
    private static boolean hasId(Object id) {
        return id != null && !(id instanceof String s && s.isBlank());
    }

    /**
     * 通过反射读取实体对象的属性值，异常时记录日志并返回 null。
     */
    private static Object readProperty(Object entity, String fieldName) {
        try {
            return PropertyUtils.getProperty(entity, fieldName);
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }
}
