package cn.geelato.core.ds;

import cn.geelato.core.ds.spi.DataSourceDefinitionLoader;
import cn.geelato.core.ds.support.DefaultDataSourceDefinitionLoader;
import cn.geelato.core.util.EncryptUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import cn.geelato.core.AbstractManager;
import cn.geelato.core.orm.Dao;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
public class DataSourceManager extends AbstractManager {


    private static DataSourceManager instance;

    private final static ConcurrentHashMap<String, DataSource> dataSourceMap=new ConcurrentHashMap<>();

    private final static ConcurrentHashMap<Object, Object> dynamicDataSourceMap =new ConcurrentHashMap<>();

    private final static ConcurrentHashMap<Object, Object> lazyDynamicDataSourceMap =new ConcurrentHashMap<>();
    private volatile String defaultDataSourceKey;
    private DataSourceDefinitionLoader definitionLoader = new DefaultDataSourceDefinitionLoader();

    public static DataSourceManager singleInstance() {
        lock.lock();
        if (instance == null) {
            instance = new DataSourceManager();
        }
        lock.unlock();
        return instance;
    }

    private DataSourceManager() {
        log.info("DataSourceManager Instancing...");
    }


    public void parseDataSourceMeta(Dao dao){
        if (dao.getJdbcTemplate().getDataSource() != null) {
            dataSourceMap.put("primary",dao.getJdbcTemplate().getDataSource());
        }
        List<Map<String,Object>> dbConenctList=definitionLoader.load(dao);
        for (Map<String,Object> dbConnectMap:dbConenctList){
            String connectId=dbConnectMap.get("id").toString();
            lazyDynamicDataSourceMap.put(connectId,dbConnectMap);
        }
    }
    public Map<Object, Object> getDynamicDataSourceMap(){
        return dynamicDataSourceMap;
    }
    public DataSource getDataSource(String connectId){
        if(dataSourceMap.get(connectId)==null){
            Object lazyDataSource=DataSourceManager.singleInstance().getLazyDataSource(connectId);
            if (!(lazyDataSource instanceof Map)) {
                return null;
            }
            DataSource dataSource=buildDataSource((Map) lazyDataSource);
            if (dataSource == null) {
                return null;
            }
            dataSourceMap.put(connectId,dataSource);
        }
        return dataSourceMap.get(connectId);
    }

    public Object getLazyDataSource(String connectId){
        return lazyDynamicDataSourceMap.get(connectId);
    }
    public DataSource buildDataSource(Map dbConnectMap){
        if (dbConnectMap == null || dbConnectMap.isEmpty()) {
            return null;
        }
        HikariConfig config = new HikariConfig();
        String dbType=dbConnectMap.get("db_type").toString().toLowerCase();
        String serverHost=dbConnectMap.get("db_hostname_ip").toString();
        String serverPort=dbConnectMap.get("db_port").toString();
        String dbUserName=dbConnectMap.get("db_user_name").toString();
        String dbPassWord=EncryptUtils.decrypt(dbConnectMap.get("db_password").toString());
        String dbName=dbConnectMap.get("db_name").toString();
        String dbDriver=null;
        String jdbcUrl=null;
        switch (dbType){
            case "mysql":
                dbDriver="com.mysql.cj.jdbc.Driver";
                String commonParams="useUnicode=true&characterEncoding=utf-8&useSSL=false&allowMultiQueries=true&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true";
                jdbcUrl=String.format("jdbc:mysql://%s:%s/%s?%s",serverHost,serverPort,dbName,commonParams);
                break;
            case "sqlserver":
                dbDriver="com.microsoft.sqlserver.jdbc.SQLServerDriver";
                jdbcUrl="jdbc:sqlserver://"+serverHost+":"+serverPort+";trustServerCertificate=true;databaseName="+dbName;
                break;
            default:
                break;
        }
        if (jdbcUrl == null || dbDriver == null) {
            return null;
        }
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(dbUserName);
        config.setPassword(dbPassWord);
        config.setDriverClassName(dbDriver);
        config.setMinimumIdle(1);
        config.setMaximumPoolSize(3);
        config.setPoolName(dbName);
        config.addDataSourceProperty("autoReconnect","true");
        config.addDataSourceProperty("maxReconnects","10");
        return new HikariDataSource(config);
    }

    public DataSourceDefinitionLoader getDefinitionLoader() {
        return definitionLoader;
    }

    public String getDefaultDataSourceKey() {
        return defaultDataSourceKey;
    }

    public void setDefaultDataSourceKey(String defaultDataSourceKey) {
        this.defaultDataSourceKey = defaultDataSourceKey;
    }

    public void registerDataSource(String key, DataSource dataSource) {
        if (key == null || key.isBlank() || dataSource == null) {
            return;
        }
        dataSourceMap.put(key, dataSource);
    }

    public void setDefinitionLoader(DataSourceDefinitionLoader definitionLoader) {
        if (definitionLoader != null) {
            this.definitionLoader = definitionLoader;
        }
    }
}
