package cn.geelato.datasource;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.utils.LocalBoundedCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体数据源解析器
 * 负责根据实体名称解析对应的数据源
 * <p>是路由体系中"实体归属档"的唯一实现（Dao 切面链与 MQL 执行器链共用，
 * 见 {@link DynamicDataSourceScope} 的优先级说明）。实体显式归属的数据源未注册时
 * 硬失败，不静默回退主库。</p>
 */
@Component
@Slf4j
public class EntityDataSourceResolver {

    /**
     * Spring 上下文实例桥（构造时登记），供非 Spring 构造的执行策略经
     * {@link #getInstance()} 取用同一份缓存与校验
     */
    private static volatile EntityDataSourceResolver instance;

    MetaManager metaManager = MetaManager.singleInstance();

    @Autowired
    private DynamicDataSourceRegistry dynamicDataSourceRegistry;

    public EntityDataSourceResolver() {
        instance = this;
    }

    /**
     * 取用当前解析器实例；脱离 Spring 上下文时回退裸实例（registry 未注入则不做注册性校验，
     * 由路由层按原行为兜底）。
     */
    public static EntityDataSourceResolver getInstance() {
        EntityDataSourceResolver resolved = instance;
        return resolved != null ? resolved : new EntityDataSourceResolver();
    }

    /**
     * 实体与数据源的映射缓存
     * Key: 实体名称, Value: 数据源键（失效由读方校验数据源存在性驱动，不设 TTL）
     */
    private final LocalBoundedCache<String, String> entityDataSourceCache = new LocalBoundedCache<>("entity-datasource", 0L, 10_000);
    
    /**
     * 根据实体名称解析数据源
     * 优先从缓存中查找，然后根据命名规则解析，最后从数据库查询
     * 
     * @param entityName 实体名称
     * @return 数据源键，如果找不到返回null
     */
    public String resolveDataSource(String entityName) {
        if (entityName == null || entityName.trim().isEmpty()) {
            return null;
        }
        // 1. 首先从缓存中查找
        String cachedDataSource = entityDataSourceCache.get(entityName);
        if (cachedDataSource != null) {
            // 验证缓存的数据源是否仍然存在
            if (checkDataSourceExists(cachedDataSource)) {
                log.debug("从缓存中找到实体 {} 的数据源: {}", entityName, cachedDataSource);
                return cachedDataSource;
            } else {
                entityDataSourceCache.remove(entityName);
                log.warn("缓存的数据源已不存在，移除缓存: {} -> {}", entityName, cachedDataSource);
            }
        }
        String metaDataSource = resolveFromMetaData(entityName);
        if (metaDataSource != null) {
            entityDataSourceCache.put(entityName, metaDataSource);
            log.debug("从实体数据中查询到实体 {} 的数据源: {}", entityName, metaDataSource);
            return metaDataSource;
        }
        log.debug("未找到实体 {} 对应的数据源", entityName);
        return null;
    }
    
    /**
     * 根据命名规则解析数据源
     * 可以根据实体名称的前缀、后缀或其他规则来确定数据源
     * 
     * @param entityName 实体名称
     * @return 数据源键，如果无法解析返回null
     */

    

    private String resolveFromMetaData(String entityName) {
        String dataSourceKey = metaManager.resolveConnectId(entityName);
        if (dataSourceKey == null) {
            return null;
        }
        if (dynamicDataSourceRegistry == null || dynamicDataSourceRegistry.containsDataSource(dataSourceKey)) {
            return dataSourceKey;
        }
        throw new IllegalStateException(String.format(
                "实体 %s 显式归属数据源 %s 未注册（platform_dev_db_connect 未登记或已被删除），拒绝静默回退主库",
                entityName, dataSourceKey));
    }
    
    /**
     * 检查数据源是否存在
     */
    private boolean checkDataSourceExists(String dataSourceName) {
        return dynamicDataSourceRegistry.containsDataSource(dataSourceName);
    }
    
    /**
     * 手动添加实体数据源映射
     */
    public void addEntityMapping(String entityName, String dataSourceName) {
        if (dynamicDataSourceRegistry.containsDataSource(dataSourceName)) {
            entityDataSourceCache.put(entityName, dataSourceName);
            log.info("添加实体数据源映射: {} -> {}", entityName, dataSourceName);
        } else {
            log.warn("数据源不存在，无法添加映射: {} -> {}", entityName, dataSourceName);
        }
    }
    
    /**
     * 移除实体数据源映射
     */
    public void removeEntityMapping(String entityName) {
        String removed = entityDataSourceCache.remove(entityName);
        if (removed != null) {
            log.info("移除实体数据源映射: {} -> {}", entityName, removed);
        }
    }
    
    /**
     * 清除所有缓存
     */
    public void clearCache() {
        entityDataSourceCache.clear();
        log.info("清除实体数据源映射缓存");
    }
    
    /**
     * 获取所有实体数据源映射
     */
    public Map<String, String> getAllMappings() {
        Map<String, String> mappings = new ConcurrentHashMap<>();
        for (String entityName : entityDataSourceCache.liveKeys()) {
            mappings.put(entityName, entityDataSourceCache.get(entityName));
        }
        return mappings;
    }
}