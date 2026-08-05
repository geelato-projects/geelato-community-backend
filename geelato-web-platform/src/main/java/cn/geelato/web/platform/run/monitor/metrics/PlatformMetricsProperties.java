package cn.geelato.web.platform.run.monitor.metrics;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 平台监控配置属性。
 * <p>
 * 绝大部分参数（actuator 端点暴露、健康分组、SLO bucket、压缩等）已由
 * {@link PlatformMetricsEnvironmentPostProcessor} 注入合理默认，用户无需配置；
 * 此类仅暴露总开关与公共标签两项可选项。
 *
 * @author geelato
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "geelato.platform.monitoring")
public class PlatformMetricsProperties {

    /**
     * 是否启用平台 OpenMetrics 监控装配（健康聚合 + 指标治理）。
     * <p>
     * 默认 true。设为 false 时仅平台自定义层失效，actuator/micrometer 本身的
     * 端点与默认指标仍可用（端点暴露由 EnvironmentPostProcessor 注入，不受此开关影响）。
     */
    private boolean enabled = true;

    /**
     * 公共标签。注入到所有指标序列，便于在多实例/多模块的 Prometheus 中区分来源。
     */
    private CommonTags commonTags = new CommonTags();

    @Setter
    @Getter
    public static class CommonTags {
        /**
         * 应用名标签，默认取 spring.application.name（由 customizer 兜底为 "unknown"）。
         */
        private String application;

        /**
         * 模块名标签，默认 "geelato-web-platform"。
         */
        private String module = "geelato-web-platform";
    }
}
