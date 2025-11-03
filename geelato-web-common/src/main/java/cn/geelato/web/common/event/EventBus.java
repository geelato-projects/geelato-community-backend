package cn.geelato.web.common.event;

import jakarta.annotation.PostConstruct;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

//@Component
public class EventBus {
    private final ApplicationEventPublisher springPublisher;
    private final List<EnhancedEventListener<?>> allListeners; // 所有监听器（自动注入）
    private final Map<Class<?>, List<EnhancedEventListener<?>>> listenerCache = new ConcurrentHashMap<>();

    // 构造器注入Spring原生发布器和所有自定义监听器
    public EventBus(ApplicationEventPublisher springPublisher, List<EnhancedEventListener<?>> allListeners) {
        this.springPublisher = springPublisher;
        this.allListeners = allListeners;
    }

    // 初始化：注册所有监听器到缓存
    @PostConstruct
    public void init() {
        allListeners.forEach(this::register);
    }

    // 注册单个监听器
    public void register(EnhancedEventListener<?> listener) {
        Assert.notNull(listener, "监听器不能为null");
        Class<?> eventType = listener.getEventType();
        // 按事件类型分组，并按优先级排序
        listenerCache.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
        listenerCache.get(eventType).sort(Comparator.comparingInt(EnhancedEventListener::getOrder));
    }

    // 发布事件（同步）
    public <T> void publish(T event) {
        Assert.notNull(event, "事件不能为null");
        // 1. 触发Spring原生事件机制（可选，兼容原生监听器）
        springPublisher.publishEvent(event);
        // 2. 触发自定义监听器
        triggerListeners(event);
    }

    // 触发匹配的监听器
    @SuppressWarnings("unchecked")
    private <T> void triggerListeners(T event) {
        Class<?> eventType = event.getClass();
        // 获取该事件类型的所有监听器
        List<EnhancedEventListener<?>> matchedListeners = listenerCache.getOrDefault(eventType, Collections.emptyList());
        // 执行监听器（过滤并按优先级）
        matchedListeners.forEach(listener -> {
            EnhancedEventListener<T> typedListener = (EnhancedEventListener<T>) listener;
            if (typedListener.accept(event)) {
                typedListener.onEvent(event);
            }
        });
    }
}
