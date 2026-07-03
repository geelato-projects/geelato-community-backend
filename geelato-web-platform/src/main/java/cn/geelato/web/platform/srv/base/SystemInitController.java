package cn.geelato.web.platform.srv.base;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import lombok.extern.slf4j.Slf4j;
import cn.geelato.core.script.sql.SqlScriptManagerFactory;
import cn.geelato.core.script.db.DbScriptManagerFactory;
import cn.geelato.datasource.DynamicDataSourceRegistry;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

@ApiRestController("/system/init")
@Slf4j
public class SystemInitController {

    private final ConfigurableEnvironment environment;
    private final ApplicationContext applicationContext;

    public SystemInitController(ConfigurableEnvironment environment, ApplicationContext applicationContext) {
        this.environment = environment;
        this.applicationContext = applicationContext;
    }

    @RequestMapping(value = {"/properties"}, method = {RequestMethod.GET})
    public ApiResult<?> properties() {
        Map<String, Map<String, String>> result = new HashMap<>();
        for (PropertySource<?> ps : environment.getPropertySources()) {
            String name = ps.getName();
            if (!(ps instanceof EnumerablePropertySource<?> eps)) continue;
            if (!isConfigFileSource(name)) continue;
            String category = categoryOfSource(name);
            Map<String, String> bucket = result.computeIfAbsent(category, k -> new LinkedHashMap<>());
            for (String key : eps.getPropertyNames()) {
                String val = null;
                try {
                    Object v = environment.getProperty(key);
                    if (v != null) val = String.valueOf(v);
                } catch (Exception ignored) {
                }
                if (val == null) {
                    Object raw = eps.getProperty(key);
                    if (raw != null) {
                        String s = String.valueOf(raw);
                        if (!s.contains("${")) val = s;
                    }
                }
                if (val == null) val = "未配置";
                String k = key.toLowerCase(Locale.ROOT);
                if (k.contains("password") || k.contains("secret")) val = "******";
                bucket.put(key, val);
            }
        }
        return ApiResult.success(result);
    }

    @RequestMapping(value = {"/datasource"}, method = {RequestMethod.GET})
    public ApiResult<?> datasource() {
        Map<String, Map<String, String>> dynamicMerge = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, DataSource> entry : getSpringManagedDataSources().entrySet()) {
                mergeDataSourceInfo(dynamicMerge, entry.getKey(), entry.getValue(), true);
            }
            if (dynamicDataSourceRegistry != null) {
                Map<String, DataSource> map = dynamicDataSourceRegistry.getAllDataSources();
                for (Map.Entry<String, DataSource> e : map.entrySet()) {
                    mergeDataSourceInfo(dynamicMerge, e.getKey(), e.getValue(), false);
                }
                Map<Object, Object> cfgMap = tryGetNamedMap(dynamicDataSourceRegistry, "dataSourceConfigMap");
                if (cfgMap != null) {
                    for (Map.Entry<Object, Object> e : cfgMap.entrySet()) {
                        String key = String.valueOf(e.getKey());
                        Map<String, String> item = dynamicMerge.get(key);
                        if (item == null) {
                            continue;
                        }
                        item.put("key", key);
                        Object val = e.getValue();
                        if (val instanceof Map<?, ?> m) {
                            String dbType = str(m.get("db_type"));
                            String host = str(m.get("db_hostname_ip"));
                            String port = str(m.get("db_port"));
                            String dbName = str(m.get("db_name"));
                            String user = str(m.get("db_user_name"));
                            item.put("dbType", dbType);
                            item.put("host", host);
                            item.put("port", port);
                            item.put("dbName", dbName);
                            item.put("username", user);
                            item.put("password", "******");
                            String url = buildJdbcUrl(dbType, host, port, dbName);
                            if (url != null) item.put("jdbcUrl", url);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        List<Map<String, String>> datasourceList = new ArrayList<>(dynamicMerge.values());
        return ApiResult.success(datasourceList);
    }

    @RequestMapping(value = {"/datasource/validate"}, method = {RequestMethod.GET})
    public ApiResult<?> validate(String key) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("key", key == null ? "" : key);
        try {
            String resolvedKey = (key == null || key.trim().isEmpty()) ? "primary" : key.trim();
            DataSource ds = getSpringManagedDataSources().get(resolvedKey);
            if (ds == null) {
                if (dynamicDataSourceRegistry == null) {
                    return ApiResult.fail("未找到数据源: " + resolvedKey);
                }
                boolean exists = dynamicDataSourceRegistry.containsDataSource(resolvedKey);
                if (!exists) {
                    return ApiResult.fail("未找到数据源: " + resolvedKey);
                }
                ds = dynamicDataSourceRegistry.getDataSource(resolvedKey);
            }
            if (ds == null) {
                return ApiResult.fail("数据源不可用: " + resolvedKey);
            }
            long start = System.nanoTime();
            boolean connected = false;
            String url = null;
            String username = null;
            String product = null;
            String driver = null;
            try (Connection conn = ds.getConnection()) {
                if (conn != null) {
                    connected = conn.isValid(3);
                    DatabaseMetaData md = conn.getMetaData();
                    if (md != null) {
                        url = md.getURL();
                        username = md.getUserName();
                        product = md.getDatabaseProductName();
                        driver = md.getDriverName();
                    }
                    if (!connected) {
                        try (Statement st = conn.createStatement();
                             ResultSet rs = st.executeQuery("SELECT 1")) {
                            connected = rs.next();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            long duration = (System.nanoTime() - start) / 1_000_000;
            result.put("connected", String.valueOf(connected));
            if (url != null) result.put("jdbcUrl", url);
            if (username != null) result.put("username", username);
            if (product != null) result.put("product", product);
            if (driver != null) result.put("driver", driver);
            result.put("durationMs", String.valueOf(duration));
            return ApiResult.success(result);
        } catch (Exception e) {
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = {"/sql"}, method = {RequestMethod.GET})
    public ApiResult<?> sql() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("db", collectDbSql());
        result.put("file", collectFileSql());
        return ApiResult.success(result);
    }

    private List<Map<String, String>> collectDbSql() {
        List<Map<String, String>> list = new ArrayList<>();
        try {
            Object dbMgr = DbScriptManagerFactory.get("db");
            Map<Object, Object> sqlMap = tryGetNamedMap(dbMgr, "sqlMap");
            if (sqlMap != null) {
                for (Map.Entry<Object, Object> e : sqlMap.entrySet()) {
                    Object k = e.getKey();
                    Object v = e.getValue();
                    if (k != null && v != null) {
                        Map<String, String> item = new LinkedHashMap<>();
                        item.put("sqlKey", String.valueOf(k));
                        item.put("sql", String.valueOf(v));
                        item.put("type", "db");
                        list.add(item);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }


    private List<Map<String, String>> collectFileSql() {
        List<Map<String, String>> list = new ArrayList<>();
        try {
            Object mgr = SqlScriptManagerFactory.get("sql");
            Object jsProvider = tryGetField(mgr, "jsProvider");
            Map<Object, Object> funcMap = tryGetNamedMap(jsProvider, "jsFunctionInfoMap");
            if (funcMap != null) {
                for (Map.Entry<Object, Object> e : funcMap.entrySet()) {
                    Object fn = e.getKey();
                    Object info = e.getValue();
                    if (fn != null && info != null) {
                        String sqlKey = String.valueOf(fn);
                        String sql = tryGetFieldString(info, "content");
                        Map<String, String> item = new LinkedHashMap<>();
                        item.put("sqlKey", sqlKey);
                        item.put("sql", sql == null ? "" : sql);
                        item.put("type", "file");
                        list.add(item);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    private Map<Object, Object> tryGetNamedMap(Object obj, String fieldName) {
        if (obj == null) return null;
        try {
            java.lang.reflect.Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object val = f.get(obj);
            if (val instanceof Map<?, ?>) return (Map<Object, Object>) val;
        } catch (Exception ignored) {
        }
        return null;
    }

    private Object tryGetField(Object obj, String fieldName) {
        if (obj == null) return null;
        try {
            java.lang.reflect.Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception ignored) {
        }
        return null;
    }

    private String tryGetFieldString(Object obj, String fieldName) {
        Object v = tryGetField(obj, fieldName);
        return v == null ? null : String.valueOf(v);
    }

    private String tryInvokeString(Object obj, String methodName) {
        if (obj == null) return null;
        try {
            java.lang.reflect.Method m = obj.getClass().getMethod(methodName);
                Object v = m.invoke(obj);
            return v == null ? null : String.valueOf(v);
        } catch (Exception ignored) {
        }
        return null;
    }

    private String tryInvokeAny(Object obj, String... names) {
        for (String n : names) {
            String v = tryInvokeString(obj, n);
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }

    private Object tryInvokeObject(Object obj, String methodName) {
        if (obj == null) return null;
        try {
            java.lang.reflect.Method m = obj.getClass().getMethod(methodName);
            return m.invoke(obj);
        } catch (Exception ignored) {
        }
        return null;
    }

    private Map<String, String> extractDataSourceProps(Object ds) {
        if (ds == null) return null;
        Map<String, String> props = new LinkedHashMap<>();
        String url = tryInvokeAny(ds, "getJdbcUrl", "getUrl");
        String user = tryInvokeAny(ds, "getUsername", "getUser");
        String pwd = tryInvokeAny(ds, "getPassword");
        if (url != null) props.put("jdbcUrl", url);
        if (user != null) props.put("username", user);
        if (pwd != null) props.put("password", maskSensitive(pwd));
        if (!props.containsKey("jdbcUrl") || !props.containsKey("username")) {
            Object wrapped = tryInvokeObject(ds, "getXaDataSource");
            if (wrapped == null) {
                wrapped = tryInvokeObject(ds, "getTargetDataSource");
                if (wrapped == null) wrapped = tryInvokeObject(ds, "getDataSource");
            }
            if (wrapped != null) {
                String wUrl = tryInvokeAny(wrapped, "getJdbcUrl", "getUrl");
                String wUser = tryInvokeAny(wrapped, "getUsername", "getUser");
                String wPwd = tryInvokeAny(wrapped, "getPassword");
                if (wUrl != null) props.put("jdbcUrl", wUrl);
                if (wUser != null) props.put("username", wUser);
                if (wPwd != null) props.put("password", maskSensitive(wPwd));
            }
        }
        return props;
    }

    private String getPropertyAny(String... keys) {
        for (String k : keys) {
            try {
                String v = environment.getProperty(k);
                if (v != null && !v.isEmpty()) return v;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private void fillDataSourceFromEnv(String key, Map<String, String> info) {
        String prefix = "spring.datasource." + key + ".";
        String url = getPropertyAny(
            prefix + "jdbc-url",
            prefix + "jdbcUrl",
            prefix + "url"
        );
        String user = getPropertyAny(
            prefix + "username",
            prefix + "user"
        );
        String pwd = getPropertyAny(prefix + "password");
        String driver = getPropertyAny(
            prefix + "driver-class-name",
            prefix + "driverClassName"
        );
        if (url != null && !info.containsKey("jdbcUrl")) info.put("jdbcUrl", url);
        if (user != null && !info.containsKey("username")) info.put("username", user);
        if (pwd != null && !info.containsKey("password")) info.put("password", maskSensitive(pwd));
        if (driver != null && !info.containsKey("driver")) info.put("driver", driver);
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private String buildJdbcUrl(String dbType, String host, String port, String dbName) {
        if (dbType == null) return null;
        String t = dbType.toLowerCase(Locale.ROOT);
        if ("mysql".equals(t)) {
            String commonParams = "useUnicode=true&characterEncoding=utf-8&useSSL=false&allowMultiQueries=true&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true";
            return String.format("jdbc:mysql://%s:%s/%s?%s", host, port, dbName, commonParams);
        }
        return null;
    }

    private void mergeDataSourceInfo(Map<String, Map<String, String>> target, String key, DataSource dataSource, boolean fillEnvFallback) {
        if (key == null || key.trim().isEmpty() || dataSource == null) return;
        Map<String, String> info = target.computeIfAbsent(key, k -> new LinkedHashMap<>());
        info.put("key", key);
        info.put("class", dataSource.getClass().getName());
        Map<String, String> props = extractDataSourceProps(dataSource);
        if (props != null) info.putAll(props);
        if (fillEnvFallback) {
            fillDataSourceFromEnv(key, info);
        }
    }

    private Map<String, DataSource> getSpringManagedDataSources() {
        Map<String, DataSource> result = new LinkedHashMap<>();
        try {
            Map<String, DataSource> beans = applicationContext.getBeansOfType(DataSource.class);
            for (Map.Entry<String, DataSource> entry : beans.entrySet()) {
                String key = resolveDataSourceKey(entry.getKey(), entry.getValue());
                if (key != null) {
                    result.put(key, entry.getValue());
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private String resolveDataSourceKey(String beanName, DataSource dataSource) {
        if (beanName == null || beanName.isEmpty() || dataSource == null) return null;
        if ("dynamicDataSource".equals(beanName) || beanName.startsWith("scopedTarget.")) return null;
        if (dataSource.getClass().getName().equals("cn.geelato.datasource.DynamicRoutingDataSource")) return null;
        if (beanName.endsWith("DataSource") && beanName.length() > "DataSource".length()) {
            return beanName.substring(0, beanName.length() - "DataSource".length());
        }
        return beanName;
    }

    private String maskSensitive(String value) {
        return value == null || value.isEmpty() ? value : "******";
    }


    @Autowired(required = false)
    private DynamicDataSourceRegistry dynamicDataSourceRegistry;

    private boolean isConfigFileSource(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains(".properties") || n.contains("application.yml") || n.contains("application.yaml") || n.contains("application.properties");
    }

    private String categoryOfSource(String sourceName) {
        String n = sourceName.toLowerCase(Locale.ROOT);
        Map<String, String> mapping = Map.of(
            "auth.properties", "auth",
            "market.properties", "market",
            "message.properties", "message",
            "oss.properties", "oss",
            "package.properties", "package",
            "sc.properties", "sc",
            "seata.properties", "seata",
            "weixin_work.properties", "weixin_work",
            "workflow.properties", "workflow"
        );
        for (Map.Entry<String, String> e : mapping.entrySet()) {
            if (n.contains(e.getKey())) return e.getValue();
        }
        return "default";
    }
}
