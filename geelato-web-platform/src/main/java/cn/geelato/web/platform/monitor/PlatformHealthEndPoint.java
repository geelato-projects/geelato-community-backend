package cn.geelato.web.platform.monitor;

import cn.geelato.lang.monitor.HealthEndpoint;
import cn.geelato.lang.monitor.HealthStatus;
import org.springframework.stereotype.Component;

@Component
public class PlatformHealthEndPoint implements HealthEndpoint {
    @Override
    public HealthStatus checkHealthStatus() {
        return new HealthStatus("平台模块", HealthStatus.Status.HEALTH);
    }
}
