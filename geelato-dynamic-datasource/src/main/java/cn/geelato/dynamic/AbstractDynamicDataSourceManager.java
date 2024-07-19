package cn.geelato.dynamic;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public abstract class AbstractDynamicDataSourceManager {
    private final static ConcurrentHashMap<Object, Object> dynamicDataSourceMap =new ConcurrentHashMap<>();

    public  Map<Object, Object> getDynamicDataSourceMap() {
        return dynamicDataSourceMap;
    }

    public void preLoadDataSourceMap() {

    }

    private void buildDataSource(List<DataSourceKeyPair> dataSourceKeyPairList) {
        for (DataSourceKeyPair dataSourceKeyPair:dataSourceKeyPairList){
            if( dataSourceKeyPair.getDatasource() instanceof HikariDataSource){
                HikariDataSource hikariDataSource = (HikariDataSource) dataSourceKeyPair.getDatasource();
            }
        }
    }

    protected abstract List<DataSourceKeyPair> loadDataSourceMap();

    private DataSource buildDataSource(Map dbConnectMap){
        HikariConfig config = new HikariConfig();
        String serverHost=dbConnectMap.get("db_hostname_ip").toString();
        String serverPort=dbConnectMap.get("db_port").toString();
        String dbName=dbConnectMap.get("db_name").toString();;
        String commonParams="useUnicode=true&characterEncoding=utf-8&useSSL=false&allowMultiQueries=true&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true";
        String jdbcUrl=String.format("jdbc:mysql://%s:%s/%s?%s",serverHost,serverPort,dbName,commonParams);
        String dbUserName=dbConnectMap.get("db_user_name").toString();
        String dbPassWord=dbConnectMap.get("db_password").toString();
        String dbDriver="com.mysql.cj.jdbc.Driver";
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(dbUserName);
        config.setPassword(dbPassWord);
        config.setDriverClassName(dbDriver);
        config.setMinimumIdle(1);
        config.setMaximumPoolSize(3);
        config.setPoolName(dbConnectMap.get("db_name").toString());
        config.addDataSourceProperty("autoReconnect","true");
        config.addDataSourceProperty("maxReconnects","10");
        return new HikariDataSource(config);
    }

}
