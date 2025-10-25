package cn.geelato.web.platform.sse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SSEManager {

    // 存储客户端连接：key=客户端标识（如用户ID），value=SseEmitter实例
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 创建连接（客户端首次请求时调用）
     */
    public SseEmitter createConnection(String clientId) {
        // 设置超时时间（0表示永不超时，根据业务调整）
        SseEmitter emitter = new SseEmitter(0L);
        // 存储连接
        emitters.put(clientId, emitter);
        // 连接关闭时移除
        emitter.onCompletion(() -> emitters.remove(clientId));
        emitter.onTimeout(() -> emitters.remove(clientId));
        emitter.onError(e -> emitters.remove(clientId));
        return emitter;
    }

    /**
     * 向指定客户端发送消息
     */
    public void sendMessage(String clientId, String message) {
        SseEmitter emitter = emitters.get(clientId);
        if (emitter != null) {
            try {
                // 发送消息（data字段为消息内容，可附加id、event等参数）
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(System.currentTimeMillis())) // 消息ID（可选）
                        .data(message) // 消息内容
                        .name("message")); // 事件类型（客户端可根据event过滤，可选）
            } catch (IOException e) {
                // 发送失败，移除连接
                emitters.remove(clientId);
                throw new RuntimeException("SSE消息发送失败", e);
            }
        }
    }

    /**
     * 关闭客户端连接
     */
    public void closeConnection(String clientId) {
        SseEmitter emitter = emitters.get(clientId);
        if (emitter != null) {
            emitter.complete(); // 主动关闭连接
            emitters.remove(clientId);
        }
    }
}
