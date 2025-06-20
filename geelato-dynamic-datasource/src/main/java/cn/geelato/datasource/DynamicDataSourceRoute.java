package cn.geelato.datasource;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DynamicDataSourceRoute extends AbstractRoutingDataSource {


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
                    setTargetDataSources(new HashMap<>(targetDataSourcesMap));
                    afterPropertiesSet();
                    log.info("动态创建并添加数据源到路由映射: {}", dataSourceKey);
                } else {
                    log.warn("无法从DynamicDataSourceRegistry获取数据源: {}", dataSourceKey);
                }
            } catch (Exception e) {
                log.error("创建或初始化数据源失败: {}", dataSourceKey, e);
            }
        }
    }
}
