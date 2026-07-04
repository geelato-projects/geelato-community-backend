package cn.geelato.datasource;

import cn.geelato.datasource.spi.DynamicDataSourceDefinitionLoader;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态数据源注册器
 * <p>负责数据源的注册、刷新、移除和生命周期管理。
 * 数据源的实际构建委托给 {@link DataSourceFactory}。</p>
 */
@Configuration
@Slf4j
public class DynamicDataSourceRegistry {

    private final JdbcTemplate primaryJdbcTemplate;
    private final JdbcTemplate secondaryJdbcTemplate;
    private final Map<String, DataSource> dataSourceMap = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> dataSourceConfigMap = new ConcurrentHashMap<>();
    private final DataSourceFactory dataSourceFactory;
    private final DynamicDataSourceProperties dynamicDataSourceProperties;
    private final DynamicDataSourceDefinitionLoader definitionLoader;
    
    /**
     * 动态数据源注册器Bean
     * 程序启动时自动加载数据源配置
     */
    public DynamicDataSourceRegistry(@Qualifier("primaryJdbcTemplate") JdbcTemplate primaryJdbcTemplate,
                                     @Qualifier("secondaryJdbcTemplate") @Nullable JdbcTemplate secondaryJdbcTemplate,
                                     DataSourceFactory dataSourceFactory,
                                     DynamicDataSourceDefinitionLoader definitionLoader,
                                     DynamicDataSourceProperties dynamicDataSourceProperties) {
        this.primaryJdbcTemplate = primaryJdbcTemplate;
        this.secondaryJdbcTemplate=secondaryJdbcTemplate;
        this.dataSourceFactory = dataSourceFactory;
        this.definitionLoader = definitionLoader;
        this.dynamicDataSourceProperties = dynamicDataSourceProperties == null ? new DynamicDataSourceProperties() : dynamicDataSourceProperties;
        try {
            refreshAllDataSources();
            log.info("For dynamic data sources has been search completed, with a total of {} data sources searched", dataSourceConfigMap.size());
        } catch (Exception e) {
            log.error("For dynamic data sources has been search failed", e);
        }
    }

    public DataSource getPrimaryDataSource() {
        return primaryJdbcTemplate.getDataSource();
    }

    public DataSource getSecondaryDataSource() {
        return secondaryJdbcTemplate == null ? null : secondaryJdbcTemplate.getDataSource();
    }

    /**
     * 全量刷新数据源配置
     */
    public synchronized int refreshAllDataSources() {
        List<Map<String, Object>> dbConnectMaps = definitionLoader.loadAll();
        Set<String> latestKeys = new HashSet<>();
        for (Map<String, Object> dbConnectMap : dbConnectMaps) {
            try {
                String key = dbConnectMap.get("id").toString();
                latestKeys.add(key);
                registerDataSource(key, dbConnectMap);
                log.info("dynamic data source config : {}", key);
            } catch (Exception e) {
                log.error("dynamic data source config failed : {}", dbConnectMap.get("db_name"), e);
            }
        }
        Set<String> removedKeys = new HashSet<>(dataSourceConfigMap.keySet());
        removedKeys.removeAll(latestKeys);
        for (String removedKey : removedKeys) {
            removeDataSource(removedKey);
        }
        return latestKeys.size();
    }

    /**
     * 刷新数据源
     */
    public synchronized boolean refreshDataSource(String key) {
        Map<String, Object> dbConnectMap = definitionLoader.loadOne(key);
        if (dbConnectMap == null || dbConnectMap.isEmpty()) {
            return false;
        }
        registerDataSource(key, dbConnectMap);
        log.info("dynamic data source refresh : {}", key);
        return true;
    }

    /**
     * 移除数据源
     */
    public synchronized void removeDataSource(String key) {
        destroyDataSource(key);
        dataSourceConfigMap.remove(key);
    }

    /**
     * 销毁数据源
     */
    public void destroyDataSource(String key) {
        DataSource dataSource = dataSourceMap.get(key);
        closeIfNecessary(key, dataSource);
        dataSourceMap.remove(key);
        log.debug("数据源销毁成功: {}", key);
    }

    /**
     * 获取数据源（懒加载）
     */
    public DataSource getDataSource(String key) {
        DataSource dataSource = dataSourceMap.get(key);
        if (dataSource == null && dataSourceConfigMap.containsKey(key)) {
            synchronized (this) {
                // 双重检查锁定
                dataSource = dataSourceMap.get(key);
                if (dataSource == null) {
                    try {
                        Map<String, Object> config = dataSourceConfigMap.get(key);
                        dataSource = dataSourceFactory.buildDataSource(config);
                        dataSourceMap.put(key, dataSource);
                        log.info("delayed create dynamic datasource : {}", key);
                    } catch (Exception e) {
                        log.error("delayed create dynamic datasource fail: {}", key, e);
                    }
                }
            }
        }
        return dataSource;
    }

    /**
     * 获取所有数据源
     */
    public Map<String, DataSource> getAllDataSources() {
        return new HashMap<>(dataSourceMap);
    }
    
    /**
     * 检查数据源是否存在
     */
    public boolean containsDataSource(String key) {
        if (key == null || key.trim().isEmpty()) {
            return false;
        }
        if ("primary".equalsIgnoreCase(key)) {
            return true;
        }
        if ("secondary".equals(key) && secondaryJdbcTemplate != null) {
            return true;
        }
        return dataSourceMap.containsKey(key) || dataSourceConfigMap.containsKey(key);
    }

    /**
     * 外部直接注册一个已构建好的数据源（不依赖 platform_dev_db_connect 表）
     * <p>适用于通过 application.properties 配置的数据源场景</p>
     *
     * @param key        数据源唯一标识
     * @param dataSource 已构建好的 DataSource 实例
     */
    public synchronized void registerDataSource(String key, DataSource dataSource) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("数据源 key 不能为空");
        }
        if (dataSource == null) {
            throw new IllegalArgumentException("数据源实例不能为 null");
        }
        destroyDataSource(key);
        dataSourceMap.put(key, dataSource);
        log.info("外部注册数据源成功: {}", key);
    }

    private void registerDataSource(String key, Map<String, Object> dbConnectMap) {
        destroyDataSource(key);
        dataSourceConfigMap.put(key, new HashMap<>(dbConnectMap));
        if (!dynamicDataSourceProperties.isDelayLoadDataSource()) {
            dataSourceMap.put(key, dataSourceFactory.buildDataSource(dbConnectMap));
        }
    }

    private void closeIfNecessary(String key, DataSource dataSource) {
        if (dataSource == null) {
            return;
        }
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            try {
                hikariDataSource.close();
            } catch (Exception e) {
                log.error("数据源销毁失败: {}", key, e);
            }
            return;
        }
        if (dataSource instanceof AutoCloseable autoCloseable) {
            try {
                autoCloseable.close();
            } catch (Exception e) {
                log.error("数据源销毁失败: {}", key, e);
            }
        }
    }
}
