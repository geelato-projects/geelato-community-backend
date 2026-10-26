package cn.geelato.script.kit.sql;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.Locale;
import java.util.Map;

/**
 * 执行库身份：dataSourceSupplier 提供的连接池解包后按 jdbc url 解析出的 (类型, host, port, 库名)。
 * 识别失败返回 null，调用方按原样透传处理（不参与跨库判定）。
 */
public record ExecutingTarget(String dbType, String host, Integer port, String db) {

    public boolean postgresFamily() {
        return "postgresql".equalsIgnoreCase(dbType) || "postgres".equalsIgnoreCase(dbType);
    }

    public boolean mysqlFamily() {
        return "mysql".equalsIgnoreCase(dbType) || "mariadb".equalsIgnoreCase(dbType);
    }

    public boolean sameServer(TableLocation location) {
        if (location == null) {
            return false;
        }
        // 双方实例地址均未知（如 H2/嵌入式库）视为同实例，由 qualifier 比较决定是否前缀
        if (location.host() == null && host == null) {
            return true;
        }
        if (location.host() == null || host == null) {
            return false;
        }
        if (!location.host().trim().equalsIgnoreCase(host.trim())) {
            return false;
        }
        int p1 = location.port() == null ? defaultPort(location.dbType()) : location.port();
        int p2 = port == null ? defaultPort(dbType) : port;
        return p1 == p2;
    }

    private static int defaultPort(String dbType) {
        if (dbType != null && (dbType.equalsIgnoreCase("postgresql") || dbType.equalsIgnoreCase("postgres"))) {
            return 5432;
        }
        return 3306;
    }

    /** 与库限定符比较（MySQL=库名；PG 执行端默认 schema 视为 public）。 */
    public boolean sameQualifier(TableLocation location) {
        if (location == null || location.qualifier() == null) {
            return false;
        }
        String mine = postgresFamily() ? "public" : db;
        return mine != null && mine.trim().equalsIgnoreCase(location.qualifier().trim());
    }

    /** 按 DataSource 身份缓存识别结果（含 null，避免反复借连接；弱引用防池泄漏）。 */
    private static final Map<DataSource, ExecutingTarget> TARGET_CACHE =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** 识别执行库身份：解包读 jdbc url，失败兜底借一次连接读 DatabaseMetaData.getURL()；仍失败返回 null（调用方透传）。 */
    public static ExecutingTarget of(DataSource dataSource) {
        if (dataSource == null) {
            return null;
        }
        synchronized (TARGET_CACHE) {
            if (TARGET_CACHE.containsKey(dataSource)) {
                return TARGET_CACHE.get(dataSource);
            }
        }
        String url = jdbcUrl(dataSource, 0);
        if (url == null) {
            url = urlFromConnection(dataSource);
        }
        ExecutingTarget target = url == null ? null : parse(url);
        synchronized (TARGET_CACHE) {
            TARGET_CACHE.put(dataSource, target);
        }
        return target;
    }

    private static String urlFromConnection(DataSource dataSource) {
        try (Connection c = dataSource.getConnection()) {
            return c.getMetaData().getURL();
        } catch (Exception e) {
            return null;
        }
    }

    static ExecutingTarget parse(String url) {
        if (url == null || !url.startsWith("jdbc:")) {
            return null;
        }
        String rest = url.substring("jdbc:".length());
        int colon = rest.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        String vendor = rest.substring(0, colon).toLowerCase(Locale.ROOT);
        String body = rest.substring(colon + 1);
        // 去掉参数
        int q = body.indexOf('?');
        if (q >= 0) {
            body = body.substring(0, q);
        }
        int semi = body.indexOf(';');
        if (semi >= 0) {
            body = body.substring(0, semi);
        }
        if (body.startsWith("//")) {
            String authority = body.substring(2);
            int slash = authority.indexOf('/');
            if (slash <= 0) {
                return null;
            }
            String hostPort = authority.substring(0, slash);
            String db = authority.substring(slash + 1);
            String host;
            Integer port = null;
            int pc = hostPort.lastIndexOf(':');
            if (pc > 0 && hostPort.indexOf(']') < pc) {
                host = hostPort.substring(0, pc);
                try {
                    port = Integer.valueOf(hostPort.substring(pc + 1));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            } else {
                host = hostPort;
            }
            return new ExecutingTarget(vendor, host, port, db.isEmpty() ? null : db);
        }
        // jdbc:h2:mem:x / jdbc:sqlite:file 等无主机形态
        if ("h2".equals(vendor) && body.startsWith("mem:")) {
            return new ExecutingTarget("h2", null, null, body.substring("mem:".length()));
        }
        if ("h2".equals(vendor) && body.startsWith("file:")) {
            return new ExecutingTarget("h2", null, null, body.substring("file:".length()));
        }
        return null;
    }

    /** 解包（DelegatingDataSource/Wrapper/反射 getRealDataSource），反射读 getJdbcUrl()/getUrl()——模块不直接依赖具体池实现。 */
    private static String jdbcUrl(DataSource dataSource, int depth) {
        if (dataSource == null || depth > 6) {
            return null;
        }
        for (String getter : new String[]{"getJdbcUrl", "getUrl"}) {
            try {
                Method m = dataSource.getClass().getMethod(getter);
                Object v = m.invoke(dataSource);
                if (v instanceof String s && !s.isBlank()) {
                    return s;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        DataSource delegate = null;
        if (dataSource instanceof DelegatingDataSource delegating) {
            delegate = delegating.getTargetDataSource();
        }
        if (delegate == null) {
            try {
                Method m = dataSource.getClass().getMethod("getRealDataSource");
                Object v = m.invoke(dataSource);
                if (v instanceof DataSource ds) {
                    delegate = ds;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        if (delegate == null) {
            try {
                Method m = dataSource.getClass().getMethod("unwrap", Class.class);
                Object v = m.invoke(dataSource, DataSource.class);
                if (v instanceof DataSource ds && ds != dataSource) {
                    delegate = ds;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return delegate == null ? null : jdbcUrl(delegate, depth + 1);
    }
}
