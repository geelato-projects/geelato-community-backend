package cn.geelato.core.enums;

import cn.geelato.core.util.ConnectUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author geemeta
 */
@Getter
@Slf4j
public enum Dialects {
    MYSQL("mysql", ConnectUtils.MYSQL_URL),
    SQLSERVER("sqlserver", ConnectUtils.SQLSERVER_URL),
    ORACLE("oracle", ConnectUtils.ORACLE_URL),
    POSTGRESQL("postgresql", ConnectUtils.POSTGRESQL_URL),
    ELASTICSEARCH("es", null),
    MONGODB("mongo", null);

    private final String type;
    private final String urlFormat;

    Dialects(String type, String urlFormat) {
        this.type = type;
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
}
