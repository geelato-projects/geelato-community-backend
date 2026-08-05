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
 * 平台就绪指标，聚合 {@link HealthEndpoint} SPI 实现，反映业务是否可接流量。
 * <p>
 * 纳入 {@code readiness} 健康分组（{@code /actuator/health/ready}）。
 * 语义对齐 K8s readiness probe：返回非 UP 时，负载均衡器/Ingress 会将该实例摘出，不再转发请求。
 * <p>
 * 判定规则：
 * <ul>
 *   <li>无任何 {@link HealthEndpoint} 实现 → {@code OUT_OF_SERVICE}（业务模块未就绪，不应接流量）</li>
 *   <li>任一 {@code ABNORMAL} → {@code OUT_OF_SERVICE}（存在异常模块，摘流量保护）</li>
 *   <li>其余（全 HEALTH 或仅 UNKNOWN）→ {@code UP}</li>
 * </ul>
 * <p>
 * 与 {@link PlatformHealthIndicator} 的差异：liveness 关心「进程是否存活」（降级不重启），
 * readiness 关心「业务是否就绪」（异常即摘流量）。二者分离避免滚动发布/单点故障时的雪崩。
 *
 * @author geelato
 */
public class PlatformReadinessIndicator implements HealthIndicator {

    private final List<HealthEndpoint> endpoints;

    public PlatformReadinessIndicator(List<HealthEndpoint> endpoints) {
        this.endpoints = endpoints == null ? List.of() : endpoints;
    }

    @Override
    public Health health() {
        if (endpoints.isEmpty()) {
            return Health.outOfService()
                    .withDetail("reason", "no HealthEndpoint registered")
                    .build();
        }

        Map<String, String> abnormal = new LinkedHashMap<>();
        for (HealthEndpoint endpoint : endpoints) {
            String module;
            HealthStatus.Status status = null;
            try {
                HealthStatus hs = endpoint.checkHealthStatus();
                if (hs != null) {
                    module = hs.getModule();
                    status = hs.getStatus();
                } else {
                    module = endpoint.getClass().getSimpleName();
                }
            } catch (Exception e) {
                module = endpoint.getClass().getSimpleName();
                status = HealthStatus.Status.ABNORMAL;
            }
            if (status == HealthStatus.Status.ABNORMAL) {
                abnormal.put(module, status.name());
            }
        }

        if (!abnormal.isEmpty()) {
            return Health.outOfService()
                    .withDetail("reason", "abnormal modules present")
                    .withDetail("abnormalModules", abnormal)
                    .build();
        }
        return Health.up()
                .withDetail("moduleCount", endpoints.size())
                .build();
    }
}
