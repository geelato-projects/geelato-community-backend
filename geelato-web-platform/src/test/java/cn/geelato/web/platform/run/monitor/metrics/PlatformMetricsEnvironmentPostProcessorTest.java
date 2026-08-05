package cn.geelato.web.platform.run.monitor.metrics;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PlatformMetricsEnvironmentPostProcessor} 零配置默认值注入测试。
 * <p>验证：缺失项注入默认、已配置项不被覆盖、openmetrics MIME 追加逻辑。
 */
class PlatformMetricsEnvironmentPostProcessorTest {

    private final PlatformMetricsEnvironmentPostProcessor processor = new PlatformMetricsEnvironmentPostProcessor();
    private final SpringApplication application = new SpringApplication();

    @Test
    void emptyEnvironment_shouldInjectDefaults() {
        MockEnvironment env = new MockEnvironment();

        processor.postProcessEnvironment(env, application);

        assertEquals("health,info,prometheus,metrics", env.getProperty("management.endpoints.web.exposure.include"));
        assertEquals("true", env.getProperty("management.endpoint.health.probes.enabled"));
        assertEquals("livenessState,platformHealth", env.getProperty("management.endpoint.health.group.liveness.include"));
        assertEquals("readinessState,platformReadiness", env.getProperty("management.endpoint.health.group.readiness.include"));
        assertEquals("true", env.getProperty("management.prometheus.metrics.export.enabled"));
        assertEquals("50ms,100ms,250ms,500ms,1s,5s",
                env.getProperty("management.metrics.distribution.slo.http.server.requests"));
        assertTrue(env.getProperty("server.compression.mime-types").contains("application/openmetrics-text"));
    }

    @Test
    void userConfiguredValues_shouldNotBeOverridden() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("management.endpoints.web.exposure.include", "health,prometheus");
        env.setProperty("management.endpoint.health.probes.enabled", "false");

        processor.postProcessEnvironment(env, application);

        // 用户显式配置优先，默认值不覆盖
        assertEquals("health,prometheus", env.getProperty("management.endpoints.web.exposure.include"));
        assertEquals("false", env.getProperty("management.endpoint.health.probes.enabled"));
    }

    @Test
    void userConfiguredCompressionMimes_shouldBeLeftUntouched() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("server.compression.mime-types", "text/html,application/json");

        processor.postProcessEnvironment(env, application);

        // 用户已配 mime-types：保持不动，不追加、不覆盖（可预期性优先）
        String mimes = env.getProperty("server.compression.mime-types");
        assertEquals("text/html,application/json", mimes);
    }

    @Test
    void defaultCompressionMimes_shouldContainOpenmetrics() {
        MockEnvironment env = new MockEnvironment();

        processor.postProcessEnvironment(env, application);

        // 用户未配 mime-types：默认值含 openmetrics，保证 prometheus 端点被 gzip 压缩
        assertTrue(env.getProperty("server.compression.mime-types").contains("application/openmetrics-text"));
    }
}
