package cn.geelato.web.common.event;

import org.springframework.core.Ordered;

public interface EnhancedEventListener<T> extends Ordered {
    // 处理事件的核心方法
    void onEvent(T event);

    // 过滤是否处理该事件（默认处理所有）
    default boolean accept(T event) {
        return true;
    }

    // 优先级（默认最低，值越小优先级越高）
    @Override
    default int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    // 关注的事件类型
    Class<T> getEventType();
}
