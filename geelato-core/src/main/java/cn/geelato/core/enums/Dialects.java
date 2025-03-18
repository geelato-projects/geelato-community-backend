package cn.geelato.core.enums;

import cn.geelato.core.util.ConnectUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * @author geemeta
 */
@Getter
@Slf4j
public enum Dialects {
    MYSQL("mysql", "com.mysql.cj.jdbc.Driver", ConnectUtils.MYSQL_URL),
    SQLSERVER("sqlserver", "com.microsoft.sqlserver.jdbc.SQLServerDriver", ConnectUtils.SQLSERVER_URL),
    ORACLE("oracle", "oracle.jdbc.driver.OracleDriver", ConnectUtils.ORACLE_URL),
    POSTGRESQL("postgresql", "org.postgresql.Driver", ConnectUtils.POSTGRESQL_URL),
    ELASTICSEARCH("es", "", null),
    MONGODB("mongo", "", null);

    private final String type;
    private final String driver;
    private final String urlFormat;

    Dialects(String type, String driver, String urlFormat) {
        this.type = type;
        this.driver = driver;
        this.urlFormat = urlFormat;
    }

    public static Dialects lookUp(String value) {
        for (Dialects dialects : Dialects.values()) {
            if (dialects.getType().equalsIgnoreCase(value)) {
                return dialects;
            }
        }
        return null;
    }

    public static String dbViewSql(String value, Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return "";
        }
        Dialects dialects = lookUp(value);
        String dbType = dialects != null ? dialects.getType() : "";
        if (Dialects.MYSQL.getType().equalsIgnoreCase(dbType)) {
            return result.get("Create View") != null ? String.valueOf(result.get("Create View")) : "";
        } else if (Dialects.SQLSERVER.getType().equalsIgnoreCase(dbType)) {
            return result.get("definition") != null ? String.valueOf(result.get("definition")) : "";
        } else if (Dialects.ORACLE.getType().equalsIgnoreCase(dbType)) {
            return result.get("TEXT") != null ? String.valueOf(result.get("TEXT")) : "";
        } else if (Dialects.POSTGRESQL.getType().equalsIgnoreCase(dbType)) {
            return result.get("definition") != null ? String.valueOf(result.get("definition")) : "";
        }
        return "";
    }
}
