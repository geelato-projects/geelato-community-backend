package cn.geelato.web.common.event;

import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

public abstract class BusinessEvent extends ApplicationEvent {
    public BusinessEvent(Object source) {
        super(source);
    }
    public abstract String getEventCode();

    public abstract void handle();
}
