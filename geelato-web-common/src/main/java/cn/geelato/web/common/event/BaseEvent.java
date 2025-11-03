package cn.geelato.web.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class BaseEvent<T> extends ApplicationEvent {

    private final String eventType;

    private final T data;
    private final String sourceId;

    public BaseEvent(Object source, String eventType, T data, String sourceId) {
        super(source);
        this.eventType = eventType;
        this.data = data;
        this.sourceId = sourceId;
    }
    public static <T> BaseEvent<T> of(Object source, String eventType, T data) {
        return new BaseEvent<>(source, eventType, data, source.getClass().getSimpleName());
    }

}
