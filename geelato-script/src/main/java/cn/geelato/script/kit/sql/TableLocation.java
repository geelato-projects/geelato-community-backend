package cn.geelato.script.kit.sql;

/**
 * 表的物理位置：所属连接 id、挂载 catalog（MySQL=db_name / PG=database）、库名限定符
 * （MySQL=db_name / PG=db_schema，用于同实例前缀改写）、实例地址与类型。
 */
public record TableLocation(String connectId, String catalog, String qualifier,
                            String host, Integer port, String dbType) {

    public boolean postgresFamily() {
        return dbType != null && (dbType.equalsIgnoreCase("postgresql") || dbType.equalsIgnoreCase("postgres"));
    }

    public boolean mysqlFamily() {
        return dbType != null && (dbType.equalsIgnoreCase("mysql") || dbType.equalsIgnoreCase("mariadb"));
    }
}
