package cn.geelato.web.common.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class EventPublisher {
    private static GlobalEventBus globalEventBus;
    @Autowired
    public void setGlobalEventBus(GlobalEventBus eventBus) {
        globalEventBus = eventBus;
    }
    public static void publish(BusinessEvent event) {
        if (globalEventBus == null) {
            throw new IllegalStateException("GlobalEventBus未初始化");
        }
        log.info(
                "发布事件 - 事件类型: {}, 事件标识: {}, 发布位置: {}#{}",
                event.getClass().getSimpleName(),
                event.getEventCode(),
                event.getSourceClass(),
                event.getSourceMethod()
        );
        globalEventBus.publish(event);
    }
}
