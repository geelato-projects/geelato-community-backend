package cn.geelato.web.common.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher {
    private static GlobalEventBus globalEventBus;
    @Autowired
    public void setGlobalEventBus(GlobalEventBus eventBus) {
        globalEventBus = eventBus;
    }
    public static void publish(BusinessEvent event) {
        if (globalEventBus == null) {
            throw new IllegalStateException("GlobalEventBus未初始化，请检查Spring配置");
        }
        globalEventBus.publish(event);
    }
}
