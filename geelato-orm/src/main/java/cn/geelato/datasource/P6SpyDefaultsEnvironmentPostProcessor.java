package cn.geelato.datasource;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * p6spy 装饰缺省配置：宿主未显式配置 {@code decorator.datasource.p6spy.*} 时补默认值
 * （slf4j appender、多行格式、默认开启），使最终 SQL 日志能力随 geelato-orm 下沉后
 * 各宿主输出一致；宿主显式配置恒优先。
 */
public class P6SpyDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_NAME = "geelatoP6SpyDefaults";
    private static final String PREFIX = "decorator.datasource.p6spy.";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        putIfMissing(environment, defaults, PREFIX + "logging", "slf4j");
        putIfMissing(environment, defaults, PREFIX + "multiline", "true");
        putIfMissing(environment, defaults, PREFIX + "enable-logging", "${GEELATO_SQL_LOG_ENABLED:true}");
        if (!defaults.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static void putIfMissing(Environment environment, Map<String, Object> properties, String key, String value) {
        if (!StringUtils.hasText(environment.getProperty(key))) {
            properties.put(key, value);
        }
    }
}
