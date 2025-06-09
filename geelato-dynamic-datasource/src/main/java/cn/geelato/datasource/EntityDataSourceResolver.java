package cn.geelato.datasource;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体数据源解析器
 * 负责根据实体名称解析对应的数据源
 */
@Component
public class EntityDataSourceResolver {
    MetaManager metaManager=MetaManager.singleInstance();
    private static final Logger logger = LoggerFactory.getLogger(EntityDataSourceResolver.class);
    
    @Autowired
    private DynamicDataSourceRegistry dynamicDataSourceRegistry;
    
    /**
     * 实体与数据源的映射缓存
     * Key: 实体名称, Value: 数据源键
     */
    private final Map<String, String> entityDataSourceCache = new ConcurrentHashMap<>();
    
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
                logger.debug("从缓存中找到实体 {} 的数据源: {}", entityName, cachedDataSource);
                return cachedDataSource;
            } else {
                entityDataSourceCache.remove(entityName);
                logger.warn("缓存的数据源已不存在，移除缓存: {} -> {}", entityName, cachedDataSource);
            }
        }
        String metaDataSource = resolveFromMetaData(entityName);
        if (metaDataSource != null) {
            entityDataSourceCache.put(entityName, metaDataSource);
            logger.debug("从实体数据中查询到实体 {} 的数据源: {}", entityName, metaDataSource);
            return metaDataSource;
        }
        logger.debug("未找到实体 {} 对应的数据源", entityName);
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
        try {
            EntityMeta entityMeta = metaManager.getByEntityName(entityName);
            if (entityMeta != null) {
                String dataSourceKey = entityMeta.getTableMeta().getConnectId();
                if (dataSourceKey != null && dynamicDataSourceRegistry.containsDataSource(dataSourceKey)) {
                    return dataSourceKey;
                }
            }
        } catch (Exception e) {
            logger.debug("从数据库查询实体映射失败: {}", entityName, e);
        }
        return null;
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
            logger.info("添加实体数据源映射: {} -> {}", entityName, dataSourceName);
        } else {
            logger.warn("数据源不存在，无法添加映射: {} -> {}", entityName, dataSourceName);
        }
    }
    
    /**
     * 移除实体数据源映射
     */
    public void removeEntityMapping(String entityName) {
        String removed = entityDataSourceCache.remove(entityName);
        if (removed != null) {
            logger.info("移除实体数据源映射: {} -> {}", entityName, removed);
        }
    }
    
    /**
     * 清除所有缓存
     */
    public void clearCache() {
        entityDataSourceCache.clear();
        logger.info("清除实体数据源映射缓存");
    }
    
    /**
     * 获取所有实体数据源映射
     */
    public Map<String, String> getAllMappings() {
        return new ConcurrentHashMap<>(entityDataSourceCache);
    }
}