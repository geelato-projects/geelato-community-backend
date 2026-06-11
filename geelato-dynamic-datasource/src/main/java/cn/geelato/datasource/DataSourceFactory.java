package cn.geelato.datasource;

import cn.geelato.core.util.EncryptUtils;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.seata.rm.datasource.DataSourceProxy;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;

/**
 * 数据源工厂
 * 负责根据数据库连接配置构建 DataSource 实例，
 * 支持 MySQL 和 PostgreSQL，并可包装 Seata DataSourceProxy。
 */
@Component
@Slf4j
public class DataSourceFactory {

    private final DbHostMapFileLoader dbHostMapFileLoader;
    private final DynamicDataSourceProperties properties;

    public DataSourceFactory(DbHostMapFileLoader dbHostMapFileLoader,
                             DynamicDataSourceProperties properties) {
        this.dbHostMapFileLoader = dbHostMapFileLoader;
        this.properties = properties == null ? new DynamicDataSourceProperties() : properties;
    }

    /**
     * 构建数据源（直接返回 HikariDataSource）
     *
     * @param dbConnectMap platform_dev_db_connect 表行记录
     * @return 配置好的 HikariDataSource
     */
    public DataSource buildDataSource(Map<String, Object> dbConnectMap) {
        ConnectionParams params = extractConnectionParams(dbConnectMap);
        HikariDataSource ds = createHikariDataSource(params);
        ds.setPoolName(params.dbId());
        return ds;
    }

    /**
     * 构建 Seata 代理数据源
     * <p>TODO: Seata 集成待定，当前保留兼容</p>
     *
     * @param dbConnectMap platform_dev_db_connect 表行记录
     * @return 包装了 DataSourceProxy 的数据源
     */
    public DataSource buildDataSourceProxy(Map<String, Object> dbConnectMap) {
        ConnectionParams params = extractConnectionParams(dbConnectMap);
        HikariDataSource ds = createHikariDataSource(params);
        return new DataSourceProxy(ds);
    }

    // ======================== 内部方法 ========================

    /**
     * 从 dbConnectMap 提取公共连接参数，并通过 DbHostMapFileLoader 做地址映射
     */
    private ConnectionParams extractConnectionParams(Map<String, Object> dbConnectMap) {
        String dbType = dbConnectMap.get("db_type").toString().toLowerCase();
        String serverHost = dbConnectMap.get("db_hostname_ip").toString();
        int serverPort = Integer.parseInt(dbConnectMap.get("db_port").toString());
        String userName = dbConnectMap.get("db_user_name").toString();
        String password = decryptDbPassword(dbConnectMap.get("db_password"));
        String dbName = dbConnectMap.get("db_name").toString();
        String dbId = dbConnectMap.get("id") == null ? null : dbConnectMap.get("id").toString();

        // 应用 host 映射
        DbHostMapFileLoader.HostPort mapped = dbHostMapFileLoader == null ? null : dbHostMapFileLoader.resolve(serverHost, serverPort);
        if (mapped != null && mapped.host() != null && !mapped.host().trim().isEmpty()) {
            serverHost = mapped.host();
            if (mapped.port() != null) {
                serverPort = mapped.port();
            }
        }
        return new ConnectionParams(dbType, serverHost, serverPort, dbName, userName, password, dbId);
    }

    /**
     * 根据连接参数创建并配置 HikariDataSource
     */
    private HikariDataSource createHikariDataSource(ConnectionParams params) {
        HikariDataSource ds = new HikariDataSource();

        if ("mysql".equals(params.dbType())) {
            String commonParams = "useUnicode=true&characterEncoding=utf-8&useSSL=false&allowMultiQueries=true"
                    + "&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true"
                    + "&connectTimeout=5000&socketTimeout=60000"
                    + "&rewriteBatchedStatements=true&cachePrepStmts=true"
                    + "&prepStmtCacheSize=256&prepStmtCacheSqlLimit=2048&useServerPrepStmts=true";
            ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
            ds.setJdbcUrl(String.format("jdbc:mysql://%s:%s/%s?%s", params.host(), params.port(), params.dbName(), commonParams));
            ds.setConnectionTestQuery(properties.getConnectionTestQuery());
        } else if ("postgresql".equals(params.dbType()) || "postgres".equals(params.dbType())) {
            String commonParams = "sslmode=disable&connectTimeout=5&socketTimeout=60&ApplicationName=geelato";
            ds.setDriverClassName("org.postgresql.Driver");
            ds.setJdbcUrl(String.format("jdbc:postgresql://%s:%s/%s?%s", params.host(), params.port(), params.dbName(), commonParams));
            ds.setConnectionTestQuery("SELECT 1");
        } else {
            throw new UnsupportedOperationException("不支持的数据库类型: " + params.dbType());
        }

        ds.setUsername(params.userName());
        ds.setPassword(params.password());

        // 连接池公共参数
        ds.setMinimumIdle(properties.getMinimumIdle());
        ds.setMaximumPoolSize(properties.getMaximumPoolSize());
        ds.setIdleTimeout(properties.getIdleTimeoutMs());
        ds.setMaxLifetime(properties.getMaxLifetimeMs());
        ds.setConnectionTimeout(properties.getConnectionTimeoutMs());
        ds.setValidationTimeout(properties.getValidationTimeoutMs());
        ds.setKeepaliveTime(properties.getKeepaliveTimeMs());
        ds.setInitializationFailTimeout(properties.getInitializationFailTimeoutMs());

        return ds;
    }

    private String decryptDbPassword(Object dbPasswordValue) {
        if (dbPasswordValue == null) {
            return null;
        }
        return EncryptUtils.decrypt(String.valueOf(dbPasswordValue));
    }

    /**
     * 封装从 dbConnectMap 解析出的数据库连接参数
     */
    private record ConnectionParams(String dbType, String host, int port,
                                    String dbName, String userName, String password, String dbId) {
    }
}
