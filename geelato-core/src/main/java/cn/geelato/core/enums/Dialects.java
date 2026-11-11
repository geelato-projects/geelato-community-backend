package cn.geelato.core.enums;

import cn.geelato.core.util.ConnectUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    /**
     * 运行期方言别名注册表：dbType → 内置方言家族（如某 PG 协议引擎 → POSTGRESQL）。
     * 框架不感知具体引擎类型，由宿主/引擎适配器自行注册；
     * 注册后驱动、URL 模板与建池按家族方言生效。
     */
    private static final Map<String, Dialects> REGISTERED_ALIASES = new ConcurrentHashMap<>();

    private final String value;
    private final String driver;
    private final String urlFormat;

    Dialects(String value, String driver, String urlFormat) {
        this.value = value;
        this.driver = driver;
        this.urlFormat = urlFormat;
    }

    public static void registerAlias(String dbType, Dialects family) {
        if (dbType == null || family == null) {
            return;
        }
        REGISTERED_ALIASES.put(dbType.trim().toLowerCase(Locale.ROOT), family);
    }

    public static void unregisterAlias(String dbType) {
        if (dbType != null) {
            REGISTERED_ALIASES.remove(dbType.trim().toLowerCase(Locale.ROOT));
        }
    }

    public static Dialects lookUp(String value) {
        for (Dialects dialects : Dialects.values()) {
            if (dialects.getValue().equalsIgnoreCase(value)) {
                return dialects;
            }
        }
        return value == null ? null : REGISTERED_ALIASES.get(value.trim().toLowerCase(Locale.ROOT));
    }

    public static String dbViewSql(String value, Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return "";
        }
        Dialects dialects = lookUp(value);
        String dbType = dialects != null ? dialects.getValue() : "";
        if (Dialects.MYSQL.getValue().equalsIgnoreCase(dbType)) {
            return result.get("Create View") != null ? String.valueOf(result.get("Create View")) : "";
        } else if (Dialects.SQLSERVER.getValue().equalsIgnoreCase(dbType)) {
            return result.get("definition") != null ? String.valueOf(result.get("definition")) : "";
        } else if (Dialects.ORACLE.getValue().equalsIgnoreCase(dbType)) {
            return result.get("TEXT") != null ? String.valueOf(result.get("TEXT")) : "";
        } else if (Dialects.POSTGRESQL.getValue().equalsIgnoreCase(dbType)) {
            return result.get("definition") != null ? String.valueOf(result.get("definition")) : "";
        }
        return "";
    }
}
