package cn.geelato.datasource;

import cn.geelato.core.util.EncryptUtils;
import com.atomikos.jdbc.AtomikosDataSourceBean;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.seata.rm.datasource.DataSourceProxy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态数据源注册器配置类
 * 负责在程序启动时动态加载数据库表内的数据库信息而形成多个数据库源
 */
@Configuration
@Slf4j
public class DynamicDataSourceRegistry {

    private final JdbcTemplate primaryJdbcTemplate;
    private final Map<String, DataSource> dataSourceMap = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> dataSourceConfigMap = new ConcurrentHashMap<>();
    private final DbHostMapFileLoader dbHostMapFileLoader;
    private final DynamicDataSourceProperties dynamicDataSourceProperties;
    
    /**
     * 动态数据源注册器Bean
     * 程序启动时自动加载数据源配置
     */
    public DynamicDataSourceRegistry(@Qualifier("primaryJdbcTemplate") JdbcTemplate primaryJdbcTemplate,
                                     DbHostMapFileLoader dbHostMapFileLoader,
                                     DynamicDataSourceProperties dynamicDataSourceProperties) {
        this.primaryJdbcTemplate= primaryJdbcTemplate;
        this.dbHostMapFileLoader = dbHostMapFileLoader;
        this.dynamicDataSourceProperties = dynamicDataSourceProperties == null ? new DynamicDataSourceProperties() : dynamicDataSourceProperties;
        try {
            loadDataSourcesFromDatabase();
            log.info("For dynamic data sources has been search completed, with a total of {} data sources searched", dataSourceConfigMap.size());
        } catch (Exception e) {
            log.error("For dynamic data sources has been search failed", e);
        }
    }

    public DataSource getPrimaryDataSource() {
        return primaryJdbcTemplate.getDataSource();
    }
    /**
     * 从数据库加载数据源配置
     */
    public void loadDataSourcesFromDatabase() {
        refreshAllDataSources();
    }

    /**
     * 全量刷新数据源配置
     */
    public synchronized int refreshAllDataSources() {
        String sql = "SELECT * FROM platform_dev_db_connect";
        List<Map<String, Object>> dbConnectMaps = primaryJdbcTemplate.queryForList(sql);
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

    //seata处理，待定
    public DataSource buildDataSourceProxy(Map<String, Object> dbConnectMap) {
        String dbType = dbConnectMap.get("db_type").toString().toLowerCase();
        if(dbType.equals("mysql")){
            String serverHost = dbConnectMap.get("db_hostname_ip").toString();
            int serverPort = Integer.parseInt(dbConnectMap.get("db_port").toString());
            String dbUserName = dbConnectMap.get("db_user_name").toString();
            String dbPassWord = decryptDbPassword(dbConnectMap.get("db_password"));
            String dbName = dbConnectMap.get("db_name").toString();
            DbHostMapFileLoader.HostPort mapped = dbHostMapFileLoader == null ? null : dbHostMapFileLoader.resolve(serverHost, serverPort);
            if (mapped != null && mapped.host() != null && !mapped.host().trim().isEmpty()) {
                serverHost = mapped.host();
                if (mapped.port() != null) {
                    serverPort = mapped.port();
                }
            }

            HikariDataSource dataSource = new HikariDataSource();
            String commonParams = "useUnicode=true&characterEncoding=utf-8&useSSL=false&allowMultiQueries=true&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true";
            String jdbcUrl = String.format("jdbc:mysql://%s:%s/%s?%s", serverHost, serverPort, dbName, commonParams);
            dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            dataSource.setJdbcUrl(jdbcUrl);
            dataSource.setUsername(dbUserName);
            dataSource.setPassword(dbPassWord);
            dataSource.setMinimumIdle(dynamicDataSourceProperties.getMinimumIdle());
            dataSource.setMaximumPoolSize(dynamicDataSourceProperties.getMaximumPoolSize());
            dataSource.setIdleTimeout(dynamicDataSourceProperties.getIdleTimeoutMs());
            dataSource.setMaxLifetime(dynamicDataSourceProperties.getMaxLifetimeMs());
            dataSource.setConnectionTimeout(dynamicDataSourceProperties.getConnectionTimeoutMs());
            dataSource.setValidationTimeout(dynamicDataSourceProperties.getValidationTimeoutMs());
            dataSource.setKeepaliveTime(dynamicDataSourceProperties.getKeepaliveTimeMs());
            dataSource.setInitializationFailTimeout(dynamicDataSourceProperties.getInitializationFailTimeoutMs());
            dataSource.setConnectionTestQuery(dynamicDataSourceProperties.getConnectionTestQuery());
            return new DataSourceProxy(dataSource);
        }else{
            return null;
        }
    }
    
    /**
     * 构建数据源
     */
    @SneakyThrows
    public DataSource buildDataSource(Map<String, Object> dbConnectMap) {
        String dbType = dbConnectMap.get("db_type").toString().toLowerCase();
        String serverHost = dbConnectMap.get("db_hostname_ip").toString();
        int serverPort = Integer.parseInt(dbConnectMap.get("db_port").toString());
        String dbUserName = dbConnectMap.get("db_user_name").toString();
        String dbPassWord = decryptDbPassword(dbConnectMap.get("db_password"));
        String dbName = dbConnectMap.get("db_name").toString();
        String dbId = dbConnectMap.get("id").toString();
        DbHostMapFileLoader.HostPort mapped = dbHostMapFileLoader == null ? null : dbHostMapFileLoader.resolve(serverHost, serverPort);
        if (mapped != null && mapped.host() != null && !mapped.host().trim().isEmpty()) {
            serverHost = mapped.host();
            if (mapped.port() != null) {
                serverPort = mapped.port();
            }
        }
        if ("mysql".equals(dbType)) {
            HikariDataSource ds = new HikariDataSource();
            ds.setPoolName(dbId);
            String commonParams = "useUnicode=true&characterEncoding=utf-8&useSSL=false&allowMultiQueries=true&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=60000&rewriteBatchedStatements=true&cachePrepStmts=true&prepStmtCacheSize=256&prepStmtCacheSqlLimit=2048&useServerPrepStmts=true";
            String jdbcUrl = String.format("jdbc:mysql://%s:%s/%s?%s", serverHost, serverPort, dbName, commonParams);
            ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
            ds.setJdbcUrl(jdbcUrl);
            ds.setUsername(dbUserName);
            ds.setPassword(dbPassWord);
            ds.setMinimumIdle(dynamicDataSourceProperties.getMinimumIdle());
            ds.setMaximumPoolSize(dynamicDataSourceProperties.getMaximumPoolSize());
            ds.setIdleTimeout(dynamicDataSourceProperties.getIdleTimeoutMs());
            ds.setMaxLifetime(dynamicDataSourceProperties.getMaxLifetimeMs());
            ds.setConnectionTimeout(dynamicDataSourceProperties.getConnectionTimeoutMs());
            ds.setValidationTimeout(dynamicDataSourceProperties.getValidationTimeoutMs());
            ds.setKeepaliveTime(dynamicDataSourceProperties.getKeepaliveTimeMs());
            ds.setInitializationFailTimeout(dynamicDataSourceProperties.getInitializationFailTimeoutMs());
            ds.setConnectionTestQuery(dynamicDataSourceProperties.getConnectionTestQuery());
            return ds;
        } else {
            throw new UnsupportedOperationException("不支持的数据库类型: " + dbType);
        }
    }



    /**
     * 刷新数据源
     */
    public synchronized boolean refreshDataSource(String key) {
        String sql = "SELECT * FROM platform_dev_db_connect WHERE id = ?";
        List<Map<String, Object>> dbConnectMaps = primaryJdbcTemplate.queryForList(sql, key);
        if (dbConnectMaps == null || dbConnectMaps.isEmpty()) {
            return false;
        }
        registerDataSource(key, dbConnectMaps.get(0));
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
        if (dataSource instanceof AtomikosDataSourceBean) {
            try {
                ((AtomikosDataSourceBean) dataSource).close();
                log.debug("数据源销毁成功: {}", key);
            } catch (Exception e) {
                log.error("数据源销毁失败: {}", key, e);
            }
        } else if (dataSource instanceof HikariDataSource) {
            try {
                ((HikariDataSource) dataSource).close();
                log.debug("数据源销毁成功: {}", key);
            } catch (Exception e) {
                log.error("数据源销毁失败: {}", key, e);
            }
        }
        dataSourceMap.remove(key);
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
                        dataSource = buildDataSource(config);
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
        return dataSourceMap.containsKey(key) || dataSourceConfigMap.containsKey(key);
    }

    private void registerDataSource(String key, Map<String, Object> dbConnectMap) {
        destroyDataSource(key);
        dataSourceConfigMap.put(key, new HashMap<>(dbConnectMap));
        if (!dynamicDataSourceProperties.isDelayLoadDataSource()) {
            dataSourceMap.put(key, buildDataSource(dbConnectMap));
        }
    }

    private String decryptDbPassword(Object dbPasswordValue) {
        if (dbPasswordValue == null) {
            return null;
        }
        return EncryptUtils.decrypt(String.valueOf(dbPasswordValue));
    }
}
