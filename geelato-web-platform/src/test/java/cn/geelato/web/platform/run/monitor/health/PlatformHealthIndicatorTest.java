package cn.geelato.web.platform.run.monitor.health;

import cn.geelato.lang.monitor.HealthEndpoint;
import cn.geelato.lang.monitor.HealthStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PlatformHealthIndicator} 聚合状态映射测试。
 * <p>验证 HealthEndpoint.Status → Spring Boot Status 的聚合规则。
 */
class PlatformHealthIndicatorTest {

    @Test
    void allHealthy_shouldBeUp() {
        PlatformHealthIndicator indicator = new PlatformHealthIndicator(List.of(
                endpoint("platform", HealthStatus.Status.HEALTH),
                endpoint("meta", HealthStatus.Status.HEALTH)
        ));

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(2, health.getDetails().get("moduleCount"));
    }

    @Test
    void anyAbnormal_shouldBeDegraded() {
        PlatformHealthIndicator indicator = new PlatformHealthIndicator(List.of(
                endpoint("platform", HealthStatus.Status.HEALTH),
                endpoint("meta", HealthStatus.Status.ABNORMAL)
        ));

        Health health = indicator.health();

        assertEquals("DEGRADED", health.getStatus().getCode());
        // 降级而非宕机，不触发 K8s liveness 重启
        assertTrue(health.getStatus().getDescription().contains("降级"));
    }

    @Test
    void onlyUnknown_shouldBeUnknown() {
        PlatformHealthIndicator indicator = new PlatformHealthIndicator(List.of(
                endpoint("meta", HealthStatus.Status.UNKNOWN)
        ));

        Health health = indicator.health();

        assertEquals(Status.UNKNOWN, health.getStatus());
    }

    @Test
    void emptyEndpoints_shouldBeUp() {
        PlatformHealthIndicator indicator = new PlatformHealthIndicator(List.of());

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(0, health.getDetails().get("moduleCount"));
    }

    @Test
    void endpointThrows_shouldBeTreatedAsAbnormalAndNotFail() {
        PlatformHealthIndicator indicator = new PlatformHealthIndicator(List.of(
                endpoint("platform", HealthStatus.Status.HEALTH),
                throwingEndpoint()
        ));

        Health health = indicator.health();

        // 单个端点抛异常不影响整体判定流程，标记为 ABNORMAL → DEGRADED
        assertEquals("DEGRADED", health.getStatus().getCode());
        assertNotNull(health.getDetails().get("modules"));
    }

    private static HealthEndpoint endpoint(String module, HealthStatus.Status status) {
        return () -> new HealthStatus(module, status);
    }

    private static HealthEndpoint throwingEndpoint() {
        return () -> {
            throw new RuntimeException("boom");
        };
    }
}
