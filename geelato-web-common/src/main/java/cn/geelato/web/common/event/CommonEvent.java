package cn.geelato.web.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class CommonEvent<T> extends ApplicationEvent {
    // Getter
    private final EventEnumInterface<T> eventEnum;

    // 业务数据
    private final T data;

    public CommonEvent(Object source, EventEnumInterface<T> eventEnum, T data) {
        super(source);
        this.eventEnum = eventEnum;
        this.data = data;
    }

    // 快捷构造方法
    public static <T> CommonEvent<T> of(Object source, EventEnumInterface<T> eventEnum, T data) {
        return new CommonEvent<>(source, eventEnum, data);
    }

}