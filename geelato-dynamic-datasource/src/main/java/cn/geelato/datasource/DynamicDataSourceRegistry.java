package cn.geelato.datasource;

import com.atomikos.jdbc.AtomikosDataSourceBean;
import com.mysql.cj.jdbc.MysqlXADataSource;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.seata.rm.datasource.DataSourceProxy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import javax.sql.XADataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态数据源注册器配置类
 * 负责在程序启动时动态加载数据库表内的数据库信息而形成多个数据库源
 */
@Configuration
@Slf4j
public class DynamicDataSourceRegistry {

    private static final boolean delayLoadDataSource = false;

    private JdbcTemplate primaryJdbcTemplate;
    private final Map<String, DataSource> dataSourceMap = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> dataSourceConfigMap = new ConcurrentHashMap<>();
    
    /**
     * 动态数据源注册器Bean
     * 程序启动时自动加载数据源配置
     */
    public DynamicDataSourceRegistry(@Qualifier("primaryJdbcTemplate") JdbcTemplate primaryJdbcTemplate) {
        try {
            this.primaryJdbcTemplate= primaryJdbcTemplate;
            loadDataSourcesFromDatabase();
            log.info("For dynamic data sources has been search completed, with a total of {} data sources searched", dataSourceConfigMap.size());
        } catch (Exception e) {
            log.error("For dynamic data sources has been search failed", e);
        }
    }
    
    /**
     * 从数据库加载数据源配置
     */
    public void loadDataSourcesFromDatabase() {
        String sql = "SELECT * FROM platform_dev_db_connect";
        List<Map<String, Object>> dbConnectMaps = primaryJdbcTemplate.queryForList(sql);
        for (Map<String, Object> dbConnectMap : dbConnectMaps) {
            try {
                String key = dbConnectMap.get("id").toString();
                dataSourceConfigMap.put(key, new HashMap<>(dbConnectMap));

                if(!delayLoadDataSource) dataSourceMap.put(key,buildDataSource(dbConnectMap));

                log.info("dynamic data source config : {}", key);
            } catch (Exception e) {
                log.error("dynamic data source config failed : {}", dbConnectMap.get("db_name"), e);
            }
        }
    }

    public DataSource buildDataSourceProxy(Map<String, Object> dbConnectMap) {
        String dbType = dbConnectMap.get("db_type").toString().toLowerCase();
        if(dbType.equals("mysql")){
            String serverHost = dbConnectMap.get("db_hostname_ip").toString();
            String serverPort = dbConnectMap.get("db_port").toString();
            String dbUserName = dbConnectMap.get("db_user_name").toString();
            String dbPassWord =dbConnectMap.get("db_password").toString();
            String dbName = dbConnectMap.get("db_name").toString();

            HikariDataSource dataSource = new HikariDataSource();
            String commonParams = "useUnicode=true&characterEncoding=utf-8&useSSL=false&allowMultiQueries=true&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true";
            String jdbcUrl = String.format("jdbc:mysql://%s:%s/%s?%s", serverHost, serverPort, dbName, commonParams);
            dataSource.setJdbcUrl(jdbcUrl);
            dataSource.setUsername(dbUserName);
            dataSource.setPassword(dbPassWord);
            return new DataSourceProxy(dataSource);
        }else{
            return null;
        }
    }
    
    /**
     * 构建数据源
     */
    public DataSource buildDataSource(Map<String, Object> dbConnectMap) {
        String dbType = dbConnectMap.get("db_type").toString().toLowerCase();
        String serverHost = dbConnectMap.get("db_hostname_ip").toString();
        String serverPort = dbConnectMap.get("db_port").toString();
        String dbUserName = dbConnectMap.get("db_user_name").toString();
        String dbPassWord =dbConnectMap.get("db_password").toString();
        String dbName = dbConnectMap.get("db_name").toString();
        String dbId = dbConnectMap.get("id").toString();
        AtomikosDataSourceBean ds = new AtomikosDataSourceBean();
        ds.setUniqueResourceName(dbId);
        
        switch (dbType) {
            case "mysql":
                ds.setXaDataSourceClassName("com.mysql.cj.jdbc.MysqlXADataSource");
                MysqlXADataSource xaDataSource = new MysqlXADataSource();
                String commonParams = "useUnicode=true&characterEncoding=utf-8&useSSL=false&allowMultiQueries=true&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true";
                String jdbcUrl = String.format("jdbc:mysql://%s:%s/%s?%s", serverHost, serverPort, dbName, commonParams);
                xaDataSource.setUrl(jdbcUrl);
                xaDataSource.setUser(dbUserName);
                xaDataSource.setPassword(dbPassWord);
                ds.setXaDataSource(xaDataSource);
                break;
            case "sqlserver":
                ds.setXaDataSourceClassName("com.microsoft.sqlserver.jdbc.SQLServerXADataSource");
                // 注意：SQL Server XA数据源配置可能需要根据实际情况调整
                break;
            default:
                throw new UnsupportedOperationException("不支持的数据库类型: " + dbType);
        }
        
        // 设置连接池参数
        ds.setMinPoolSize(1);
        ds.setMaxPoolSize(3);
        ds.setMaxIdleTime(60);
        ds.setBorrowConnectionTimeout(30);
        
        return ds;
    }



    /**
     * 刷新数据源
     */
    public void refreshDataSource(String key) {
        try {
            String sql = "SELECT * FROM platform_dev_db_connect WHERE db_name = ?";
            Map<String, Object> dbConnectMap = primaryJdbcTemplate.queryForMap(sql, key);
            destroyDataSource(key);
            dataSourceConfigMap.put(key, new HashMap<>(dbConnectMap));
            log.info("dynamic data source refresh : {}", key);
        } catch (Exception e) {
            log.error("dynamic data source refresh failed: {}", key, e);
        }
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
        if(!dataSourceMap.containsKey(key)){
            if(dataSourceConfigMap.containsKey(key)){
                dataSourceMap.put(key,getDataSource(key));
            }
        }
        return dataSourceMap.containsKey(key);
    }
}
