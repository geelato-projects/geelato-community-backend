package cn.geelato.datasource;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DynamicRoutingDataSource extends AbstractRoutingDataSource {


    @Setter
    private DynamicDataSourceRegistry dynamicDataSourceRegistry;

    private final Map<Object, DataSource> targetDataSourcesMap = new HashMap<>();
    
    @Override
    protected Object determineCurrentLookupKey() {
        String dataSourceKey = DynamicDataSourceHolder.getDataSourceKey();
        if (dataSourceKey != null && !dataSourceKey.trim().isEmpty()) {
            ensureDataSourceExists(dataSourceKey);
        }
        return dataSourceKey;
    }

    private void ensureDataSourceExists(String dataSourceKey) {
        if (targetDataSourcesMap.containsKey(dataSourceKey)) {
            return;
        }
        synchronized (this) {
            if (targetDataSourcesMap.containsKey(dataSourceKey)) {
                return;
            }
            try {
                DataSource dataSource = dynamicDataSourceRegistry.getDataSource(dataSourceKey);
                if (dataSource != null) {
                    targetDataSourcesMap.put(dataSourceKey, dataSource);
                    applyTargetDataSources();
                    log.info("动态创建并添加数据源到路由映射: {}", dataSourceKey);
                } else {
                    log.warn("无法从DynamicDataSourceRegistry获取数据源: {}", dataSourceKey);
                }
            } catch (Exception e) {
                log.error("创建或初始化数据源失败: {}", dataSourceKey, e);
            }
        }
    }

    public synchronized void refreshDataSource(String dataSourceKey) {
        if (dataSourceKey == null || dataSourceKey.trim().isEmpty() || "primary".equalsIgnoreCase(dataSourceKey)) {
            return;
        }
        targetDataSourcesMap.remove(dataSourceKey);
        DataSource dataSource = dynamicDataSourceRegistry.getDataSource(dataSourceKey);
        if (dataSource != null) {
            targetDataSourcesMap.put(dataSourceKey, dataSource);
        }
        applyTargetDataSources();
    }

    public synchronized void refreshAllDataSources() {
        targetDataSourcesMap.clear();
        targetDataSourcesMap.putAll(dynamicDataSourceRegistry.getAllDataSources());
        applyTargetDataSources();
    }

    private void applyTargetDataSources() {
        Map<Object, Object> targetDataSources = new HashMap<>();
        DataSource primaryDataSource = dynamicDataSourceRegistry.getPrimaryDataSource();
        DataSource secondaryDataSource = dynamicDataSourceRegistry.getSecondaryDataSource();
        if (primaryDataSource == null) {
            throw new IllegalStateException("Primary data source must not be null");
        }
        targetDataSources.put("primary", primaryDataSource);
        if (secondaryDataSource != null) {
            targetDataSources.put("secondary", secondaryDataSource);
        }
        targetDataSourcesMap.forEach((key, dataSource) -> {
            if (dataSource != null) {
                targetDataSources.put(key, dataSource);
            }
        });
        setTargetDataSources(targetDataSources);
        setDefaultTargetDataSource(primaryDataSource);
        afterPropertiesSet();
    }
}
