package cn.geelato.web.platform.sse;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
@Component
public class SseEmitterManager {
    // 核心映射：主题 -> 订阅者集合（线程安全）
    private final Map<String, Set<SseEmitter>> topicSubscribers = new ConcurrentHashMap<>();

    /**
     * 订阅主题：创建SseEmitter并关联到主题
     */
    public SseEmitter subscribe(String topic) {
        // 1. 创建Emitter（设置超时时间，避免连接长期闲置）
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L); // 30分钟超时

        // 2. 注册断开回调：客户端断开后从订阅者集合中移除
        emitter.onCompletion(() -> removeEmitter(topic, emitter));
        emitter.onTimeout(() -> removeEmitter(topic, emitter));
        emitter.onError((e) -> removeEmitter(topic, emitter));

        // 3. 将Emitter添加到主题的订阅者集合（主题不存在则自动创建）
        topicSubscribers.computeIfAbsent(topic, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        return emitter;
    }

    /**
     * 向主题推送消息：遍历订阅者并推送
     */
    public void sendToTopic(String topic, Object message) {
        // 1. 获取主题的订阅者集合（无订阅者则直接返回）
        Set<SseEmitter> emitters = topicSubscribers.get(topic);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        // 2. 遍历订阅者，推送消息（失败则移除无效连接）
        for (SseEmitter emitter : emitters) {
            try {
                // 发送SSE格式消息（包含数据和重连时间）
                emitter.send(SseEmitter.event()
                        .data(message, MediaType.APPLICATION_JSON) // 指定JSON格式
                        .reconnectTime(3000)); // 断线后3秒重连
            } catch (IOException e) {
                // 推送失败（如客户端已断开），移除该Emitter
                removeEmitter(topic, emitter);
            }
        }
    }

    /**
     * 从主题中移除订阅者
     */
    private void removeEmitter(String topic, SseEmitter emitter) {
        Set<SseEmitter> emitters = topicSubscribers.get(topic);
        if (emitters != null) {
            emitters.remove(emitter);
            // 如果主题已无订阅者，可选择删除主题（节省内存）
            if (emitters.isEmpty()) {
                topicSubscribers.remove(topic);
            }
        }
    }

    // 辅助方法：获取所有活跃主题
    public Set<String> getActiveTopics() {
        return topicSubscribers.keySet();
    }
}
