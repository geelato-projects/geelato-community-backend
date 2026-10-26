package cn.geelato.datasource;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class P6SpyDefaultsEnvironmentPostProcessorTest {

    private final P6SpyDefaultsEnvironmentPostProcessor processor = new P6SpyDefaultsEnvironmentPostProcessor();

    @Test
    void contributesDefaultsWhenHostHasNoExplicitConfig() {
        StandardEnvironment environment = new StandardEnvironment();
        processor.postProcessEnvironment(environment, null);

        assertNotNull(environment.getPropertySources().get(P6SpyDefaultsEnvironmentPostProcessor.PROPERTY_SOURCE_NAME));
        assertEquals("slf4j", environment.getProperty("decorator.datasource.p6spy.logging"));
        assertEquals("true", environment.getProperty("decorator.datasource.p6spy.multiline"));
        assertNotNull(environment.getProperty("decorator.datasource.p6spy.enable-logging"));
    }

    @Test
    void hostExplicitConfigWinsOverDefaults() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("hostConfig",
                Map.of("decorator.datasource.p6spy.logging", "custom-appender")));
        processor.postProcessEnvironment(environment, null);

        assertEquals("custom-appender", environment.getProperty("decorator.datasource.p6spy.logging"));
        assertEquals("true", environment.getProperty("decorator.datasource.p6spy.multiline"));
    }

    @Test
    void enableLoggingPlaceholderHonorsSqlLogEnabledToggle() {
        StandardEnvironment environment = new StandardEnvironment();
        processor.postProcessEnvironment(environment, null);
        environment.getPropertySources().addFirst(new MapPropertySource("toggle",
                Map.of("GEELATO_SQL_LOG_ENABLED", "false")));

        assertEquals("false", environment.getProperty("decorator.datasource.p6spy.enable-logging"));
    }

    @Test
    void repeatedProcessingIsIdempotent() {
        StandardEnvironment environment = new StandardEnvironment();
        processor.postProcessEnvironment(environment, null);
        processor.postProcessEnvironment(environment, null);

        assertTrue(environment.getPropertySources().stream()
                .filter(ps -> ps.getName().equals(P6SpyDefaultsEnvironmentPostProcessor.PROPERTY_SOURCE_NAME))
                .count() == 1);
    }
}
