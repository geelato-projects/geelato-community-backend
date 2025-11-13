package cn.geelato.web.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;


@Getter
public abstract class BusinessEvent extends ApplicationEvent {
    private final String sourceClass;
    private final String sourceMethod;
    public BusinessEvent(Object source) {
        super(source);
        StackTraceElement publishElement = Thread.currentThread().getStackTrace()[3];
        this.sourceClass = publishElement.getClassName();
        this.sourceMethod = publishElement.getMethodName();
    }
    public abstract String getEventCode();

    public abstract void handle();
}
