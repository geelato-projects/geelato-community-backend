package cn.geelato.web.platform.run.monitor.health;

import cn.geelato.lang.monitor.HealthEndpoint;
import cn.geelato.lang.monitor.HealthStatus;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台健康聚合指标，聚合所有 {@link HealthEndpoint} SPI 实现。
 * <p>
 * 纳入 {@code liveness} 健康分组（{@code /actuator/health/live}），反映服务进程整体健康度。
 * 语义对齐 K8s liveness probe：用于判断是否需要重启容器。
 * <p>
 * 状态映射：
 * <ul>
 *   <li>所有实现均为 {@code HEALTH} → {@code UP}</li>
 *   <li>任一为 {@code ABNORMAL} → 整体 {@code DEGRADED}（部分功能不可用，但进程仍存活，不应重启）</li>
 *   <li>仅含 {@code UNKNOWN} 且无 {@code ABNORMAL} → {@code UNKNOWN}</li>
 * </ul>
 * <p>
 * DEGRADED 映射为 HTTP 200（由 EnvironmentPostProcessor 默认配置），避免 K8s 因非致命降级触发重启；
 * 告警由 {@code geelato_platform_health_status} 指标侧触发，而非 HTTP 状态码。
 *
 * @author geelato
 */
public class PlatformHealthIndicator implements HealthIndicator {

    private final List<HealthEndpoint> endpoints;

    public PlatformHealthIndicator(List<HealthEndpoint> endpoints) {
        this.endpoints = endpoints == null ? List.of() : endpoints;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        boolean hasAbnormal = false;
        boolean hasUnknown = false;
        boolean hasHealth = false;

        for (HealthEndpoint endpoint : endpoints) {
            String module = "unknown";
            HealthStatus.Status status = null;
            String detail = null;
            try {
                HealthStatus hs = endpoint.checkHealthStatus();
                if (hs != null) {
                    module = hs.getModule();
                    status = hs.getStatus();
                    detail = hs.getDetails();
                }
            } catch (Exception e) {
                // 单个端点抛异常不应影响整体判定，标记为 ABNORMAL 并记录
                module = moduleLabel(endpoint);
                status = HealthStatus.Status.ABNORMAL;
                detail = e.getClass().getSimpleName() + ": " + e.getMessage();
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("status", status == null ? null : status.name());
            if (detail != null) {
                entry.put("details", detail);
            }
            details.put(module, entry);

            if (status == HealthStatus.Status.HEALTH) {
                hasHealth = true;
            } else if (status == HealthStatus.Status.ABNORMAL) {
                hasAbnormal = true;
            } else if (status == HealthStatus.Status.UNKNOWN) {
                hasUnknown = true;
            }
        }

        Status aggregate = aggregateStatus(hasAbnormal, hasUnknown, hasHealth);
        return Health.status(aggregate)
                .withDetail("modules", details)
                .withDetail("moduleCount", endpoints.size())
                .build();
    }

    /**
     * 聚合状态：任一 ABNORMAL → DEGRADED；否则仅含 UNKNOWN → UNKNOWN；其余 → UP。
     * <p>
     * 不直接返回 DOWN：平台进程本身存活，模块级异常属于「降级」而非「宕机」，
     * 避免触发 K8s liveness 重启（由 readiness 决定是否摘流量）。
     */
    static Status aggregateStatus(boolean hasAbnormal, boolean hasUnknown, boolean hasHealth) {
        if (hasAbnormal) {
            return new Status("DEGRADED", "部分模块异常，平台降级运行");
        }
        if (hasUnknown) {
            return Status.UNKNOWN;
        }
        return Status.UP;
    }

    private String moduleLabel(HealthEndpoint endpoint) {
        return endpoint == null ? "unknown" : endpoint.getClass().getSimpleName();
    }
}
