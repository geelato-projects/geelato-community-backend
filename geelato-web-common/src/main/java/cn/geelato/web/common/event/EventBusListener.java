package cn.geelato.web.common.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class EventBusListener {
    @EventListener
    public void onEvent(BusinessEvent event) {
        event.handle();
        log.info("事件总线处理事件：{}", event.getEventCode());
    }
}
