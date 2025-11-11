package cn.geelato.web.common.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class GlobalEventBus {
    @Autowired
    private ApplicationEventMulticaster eventMulticaster;

    public void publish(BusinessEvent event) {
        log.info("事件总线发布事件：{}", event.getEventCode());
        eventMulticaster.multicastEvent(event);
    }
}
