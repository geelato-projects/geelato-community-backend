package cn.geelato.datasource.transaction;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class AtomikosRuntimePropertiesInitializer {
    public static final String TM_UNIQUE_NAME_PROPERTY = "com.atomikos.icatch.tm_unique_name";
    public static final String LOG_BASE_DIR_PROPERTY = "com.atomikos.icatch.log_base_dir";
    private static final String SPRING_TM_ID_PROPERTY = "spring.jta.transaction-manager-id";
    private static final String SPRING_LOG_BASE_DIR_PROPERTY = "spring.jta.atomikos.properties.log-base-dir";
    private static final String PROPERTY_SOURCE_NAME = "geelatoAtomikosRuntimeProperties";
    private static final String DEFAULT_TM_NAME_PREFIX = "dynamic-xa-";
    private static final Object INIT_LOCK = new Object();

    private AtomikosRuntimePropertiesInitializer() {
    }

    public static void initialize() {
        initialize(null);
    }

    public static void initialize(Environment environment) {
        synchronized (INIT_LOCK) {
            String transactionManagerName = resolveTransactionManagerName(environment);
            Path logBaseDir = resolveLogBaseDir(environment, transactionManagerName);
            createDirectories(logBaseDir);
            System.setProperty(TM_UNIQUE_NAME_PROPERTY, transactionManagerName);
            System.setProperty(LOG_BASE_DIR_PROPERTY, logBaseDir.toString());
        }
    }

    public static void contributeEnvironmentDefaults(ConfigurableEnvironment environment) {
        initialize(environment);
        Map<String, Object> properties = new LinkedHashMap<>();
        putIfMissing(environment, properties, SPRING_TM_ID_PROPERTY, System.getProperty(TM_UNIQUE_NAME_PROPERTY));
        putIfMissing(environment, properties, SPRING_LOG_BASE_DIR_PROPERTY, System.getProperty(LOG_BASE_DIR_PROPERTY));
        if (!properties.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
        }
    }

    public static String getTransactionManagerName() {
        initialize();
        return System.getProperty(TM_UNIQUE_NAME_PROPERTY);
    }

    public static Path getAtomikosLogBaseDir() {
        initialize();
        return Paths.get(System.getProperty(LOG_BASE_DIR_PROPERTY));
    }

    private static String resolveTransactionManagerName(Environment environment) {
        String configured = firstNonBlank(
                System.getProperty(TM_UNIQUE_NAME_PROPERTY),
                getProperty(environment, SPRING_TM_ID_PROPERTY)
        );
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        return DEFAULT_TM_NAME_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static Path resolveLogBaseDir(Environment environment, String transactionManagerName) {
        String configured = firstNonBlank(
                System.getProperty(LOG_BASE_DIR_PROPERTY),
                getProperty(environment, SPRING_LOG_BASE_DIR_PROPERTY)
        );
        if (StringUtils.hasText(configured)) {
            return Paths.get(configured);
        }
        return Paths.get(".", "atomikos-logs", transactionManagerName);
    }

    private static void createDirectories(Path logBaseDir) {
        try {
            Files.createDirectories(logBaseDir);
        } catch (IOException e) {
            throw new RuntimeException("创建 Atomikos 日志目录失败: " + logBaseDir, e);
        }
    }

    private static void putIfMissing(Environment environment, Map<String, Object> properties, String key, String value) {
        if (!StringUtils.hasText(environment.getProperty(key)) && StringUtils.hasText(value)) {
            properties.put(key, value);
        }
    }

    private static String getProperty(Environment environment, String key) {
        return environment == null ? null : environment.getProperty(key);
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
