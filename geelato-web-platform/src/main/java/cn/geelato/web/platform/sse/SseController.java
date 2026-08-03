package cn.geelato.web.platform.sse;

import cn.geelato.security.SecurityContext;
import cn.geelato.web.platform.srv.notification.channel.InAppChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/subscribe")
public class SseController {
    @Autowired
    private SseEmitterManager sseManager;

    @GetMapping(value = "/{topic}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable String topic) {
        if (topic == null || topic.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "topic不能为空");
        }
        // 个人通知主题安全校验：只允许订阅自己的 notice_user_${userId}
        // 防止越权订阅他人通知
        if (topic.startsWith(InAppChannel.USER_TOPIC_PREFIX)) {
            String ownerUserId = topic.substring(InAppChannel.USER_TOPIC_PREFIX.length());
            String currentUserId = null;
            try {
                currentUserId = SecurityContext.getCurrentUser().getUserId();
            } catch (Exception ignored) {
            }
            if (currentUserId == null || !currentUserId.equals(ownerUserId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权订阅该主题");
            }
        }
        return SseHelper.subscribe(topic);
    }

    @GetMapping(value = "/topic/all", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter all() {
        return SseHelper.subscribeAll();
    }
}
