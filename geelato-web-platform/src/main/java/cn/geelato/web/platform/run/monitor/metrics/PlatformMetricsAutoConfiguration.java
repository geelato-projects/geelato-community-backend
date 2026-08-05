package cn.geelato.web.platform.run.monitor.metrics;

import cn.geelato.lang.monitor.HealthEndpoint;
import cn.geelato.web.platform.run.monitor.health.PlatformHealthIndicator;
import cn.geelato.web.platform.run.monitor.health.PlatformReadinessIndicator;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * 平台 OpenMetrics 监控自动装配。
 * <p>
 * 仅当 classpath 存在 {@link MeterRegistry}（即引入了 micrometer）且
 * {@code geelato.platform.monitoring.enabled=true}（默认）时装配。
 * <p>
 * 平台通过 {@code @ComponentScan("cn.geelato")} 接管本类的 Bean 注册，
 * 故无需 spring.factories 的 {@code EnableAutoConfiguration} 登记；
 * 仅 {@link PlatformMetricsEnvironmentPostProcessor} 需经 spring.factories 注册（环境准备阶段执行）。
 * <p>
 * 装配内容：
 * <ul>
 *   <li>{@link PlatformMeterCustomizer}：公共标签 + 高基数 MeterFilter</li>
 *   <li>{@link PlatformHealthIndicator}（bean platformHealth）：聚合 HealthEndpoint，纳入 liveness</li>
 *   <li>{@link PlatformReadinessIndicator}（bean platformReadiness）：聚合 HealthEndpoint，纳入 readiness</li>
 * </ul>
 * <p>
 * 参考 geelato-message 的 {@code MessageAutoConfiguration} 同款条件装配风格。
 *
 * @author geelato
 */
@Configuration
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "geelato.platform.monitoring", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PlatformMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MeterRegistryCustomizer<MeterRegistry> platformMeterRegistryCustomizer(
            PlatformMetricsProperties properties, Environment environment) {
        return new PlatformMeterCustomizer(properties, environment);
    }

    @Bean("platformHealth")
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnMissingBean(name = "platformHealth")
    public HealthIndicator platformHealthIndicator(List<HealthEndpoint> endpoints) {
        return new PlatformHealthIndicator(endpoints);
    }

    @Bean("platformReadiness")
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnMissingBean(name = "platformReadiness")
    public HealthIndicator platformReadinessIndicator(List<HealthEndpoint> endpoints) {
        return new PlatformReadinessIndicator(endpoints);
    }
}
