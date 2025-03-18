package cn.geelato.core.util;

import cn.geelato.core.enums.Dialects;
import cn.geelato.core.meta.model.connect.ConnectMeta;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * @author diabl
 */
@Slf4j
public class ConnectUtils {
    public static final String MYSQL_URL = "jdbc:mysql://%s:%s/%s?useUnicode=true&characterEncoding=utf-8&useSSL=false&allowMultiQueries=true";
    public static final String SQLSERVER_URL = "jdbc:sqlserver://%s:%s;trustServerCertificate=true;databaseName=%s";
    public static final String ORACLE_URL = "jdbc:oracle:thin:@%s:%s:%s";
    public static final String POSTGRESQL_URL = "jdbc:postgresql://%s:%s/%s";
    public static final int CONNECT_TIMEOUT = 10;

    public static Connection getConnection(ConnectMeta meta) throws SQLException, ClassNotFoundException {
        if (meta == null) {
            log.warn("Get connection failed: connectMeta is null");
            return null;
        }
        Dialects dialects = Dialects.lookUp(meta.getDbType());
        if (dialects == null) {
            log.error("Get connection failed: database type not find in dialects");
            return null;
        }
        String jdbcUrl = ConnectUtils.getJdbcUrl(dialects.getUrlFormat(), meta.getDbHostnameIp(), meta.getDbPort(), meta.getDbName());
        Class.forName(dialects.getDriver());
        return ConnectUtils.getConnection(jdbcUrl, meta.getDbUserName(), meta.getDbPassword());
    }

    public static String jdbcUrl(String dbType, String host, int port, String databaseName) {
        Dialects dialects = Dialects.lookUp(dbType);
        if (dialects == null) {
            log.error("Connection test failed: database type not find in dialects");
            return "";
        }
        return ConnectUtils.getJdbcUrl(dialects.getUrlFormat(), host, port, databaseName);
    }

    public static boolean connectionTest(ConnectMeta meta) throws ClassNotFoundException {
        if (meta == null) {
            log.error("Connection test failed: connectMeta is null");
            return false;
        }
        Dialects dialects = Dialects.lookUp(meta.getDbType());
        if (dialects == null) {
            log.error("Connection test failed: database type not find in dialects");
            return false;
        }
        String jdbcUrl = ConnectUtils.getJdbcUrl(dialects.getUrlFormat(), meta.getDbHostnameIp(), meta.getDbPort(), meta.getDbName());
        Class.forName(dialects.getDriver());
        return ConnectUtils.connectionTest(jdbcUrl, meta.getDbUserName(), meta.getDbPassword());
    }

    private static Boolean connectionTest(String jdbcUrl, String userName, String password) {
        // 使用 try-with-resources 自动管理连接
        try (Connection connection = ConnectUtils.getConnection(jdbcUrl, userName, password)) {
            // 检查连接是否有效
            return connection != null && connection.isValid(ConnectUtils.CONNECT_TIMEOUT);
        } catch (SQLException e) {
            log.error("Connection test failed: {}", e.getMessage(), e);
            return false;
        }
    }

    private static Connection getConnection(String jdbcUrl, String userName, String password) throws SQLException {
        // 参数校验
        if (StringUtils.isAllBlank(jdbcUrl, userName, password)) {
            log.warn("Connection test failed: invalid parameters");
            return null;
        }
        // 构建连接 URL
        return DriverManager.getConnection(jdbcUrl, userName, password);
    }

    private static String getJdbcUrl(String urlFormat, String host, int port, String databaseName) {
        // 参数校验
        if (StringUtils.isAllBlank(urlFormat, host, Integer.toString(port), databaseName)) {
            log.warn("Connection test failed: invalid parameters");
            return null;
        }
        // 构建连接 URL
        return String.format(urlFormat, host, port, databaseName);
    }
}
