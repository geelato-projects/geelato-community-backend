package cn.geelato.web.platform.sse;

import cn.geelato.web.platform.run.SpringContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.Set;

public class SseHelper {
    private static final SseEmitterManager sseEmitterManager;

    static {
        try {
            sseEmitterManager = SpringContextHolder.getBean(SseEmitterManager.class);
        } catch (Exception e) {
            throw new RuntimeException("SseHelper初始化失败：无法获取SseEmitterManager", e);
        }
    }

    /**
     * 推送SSE消息（接收包含主题和数据的SseMessage对象）
     * @param sseMessage 包含主题(topic)和数据(data)的对象
     */
    public static void push(SseMessage sseMessage) {
        if (sseMessage == null || sseMessage.getTopic() == null) {
            throw new IllegalArgumentException("SSE消息主题不能为空");
        }
        if (sseEmitterManager == null) {
            throw new IllegalStateException("SseEmitterManager未初始化");
        }
        // 调用管理器推送消息（主题和数据从SseMessage中获取）
        sseEmitterManager.sendToTopic(sseMessage.getTopic(), sseMessage.getData());
    }
    public static SseEmitter subscribe(String topic) {
        if (sseEmitterManager == null) {
            throw new IllegalStateException("SseEmitterManager未初始化");
        }
        return sseEmitterManager.subscribe(topic);
    }

    public static SseEmitter subscribeAll() {
        if (sseEmitterManager == null) {
            throw new IllegalStateException("SseEmitterManager未初始化");
        }
        return sseEmitterManager.subscribeAll();
    }

    public static Set<String> getActiveTopics() {
        return sseEmitterManager != null ? sseEmitterManager.getActiveTopics() : Collections.emptySet();
    }
}
