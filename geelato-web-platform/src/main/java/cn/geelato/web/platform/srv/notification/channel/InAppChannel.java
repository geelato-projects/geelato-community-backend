package cn.geelato.web.platform.srv.notification.channel;

import cn.geelato.meta.Notification;
import cn.geelato.web.platform.srv.notification.dto.ChannelResult;
import cn.geelato.web.platform.srv.notification.enums.NotificationChannelEnum;
import cn.geelato.web.platform.srv.notification.service.NotificationUserService;
import cn.geelato.web.platform.sse.SseHelper;
import cn.geelato.web.platform.sse.SseMessage;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 站内信投递渠道（内置默认）。
 * <ol>
 *   <li>为每个收件人写一行未读状态 platform_notification_user（fan-out，靠唯一键幂等）</li>
 *   <li>通过 SSE 实时推送到收件人的个人主题 notice_user_${userId}（在线即收）</li>
 * </ol>
 * SSE 推送负载为通知摘要 DTO，前端收到后角标 +1 并 prepend 列表。
 *
 * @author geelato
 */
@Component
@Slf4j
public class InAppChannel implements DeliveryChannel {

    /** 个人通知主题前缀，订阅时 {@code /subscribe/notice_user_${userId}}，需归属校验 */
    public static final String USER_TOPIC_PREFIX = "notice_user_";

    private final NotificationUserService notificationUserService;

    @Autowired
    public InAppChannel(NotificationUserService notificationUserService) {
        this.notificationUserService = notificationUserService;
    }

    @Override
    public String getChannel() {
        return NotificationChannelEnum.INAPP.value();
    }

    @Override
    public ChannelResult deliver(Notification notification, List<String> userIds) {
        if (notification == null || userIds == null || userIds.isEmpty()) {
            return ChannelResult.ok();
        }
        try {
            // 1. fan-out 写收件人状态
            notificationUserService.fanOutToInbox(notification.getId(), userIds, resolveOperator(notification));

            // 2. SSE 实时推送（在线用户即时收到，离线用户下次加载收件箱时看到）
            Map<String, Object> payload = toPushPayload(notification);
            for (String userId : userIds) {
                if (userId == null || userId.isBlank()) {
                    continue;
                }
                try {
                    SseHelper.push(new SseMessage(USER_TOPIC_PREFIX + userId, payload));
                } catch (Exception e) {
                    // 单个用户推送失败不影响整体（数据已落库，下次加载可见）
                    log.warn("站内信 SSE 推送失败 userId={}, notificationId={}: {}", userId, notification.getId(), e.getMessage());
                }
            }
            return ChannelResult.ok();
        } catch (Exception e) {
            log.error("站内信投递失败 notificationId={}: {}", notification.getId(), e.getMessage(), e);
            return ChannelResult.fail(e.getMessage());
        }
    }

    private String resolveOperator(Notification notification) {
        return notification.getCreator() != null ? notification.getCreator() : "system";
    }

    /** 构造前端推送负载：通知摘要 + 跳转地址 */
    private Map<String, Object> toPushPayload(Notification notification) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", notification.getId());
        payload.put("title", notification.getTitle());
        payload.put("content", notification.getContent());
        payload.put("senderId", notification.getSenderId());
        payload.put("senderName", notification.getSenderName());
        payload.put("senderType", notification.getSenderType());
        payload.put("bizType", notification.getBizType());
        payload.put("bizId", notification.getBizId());
        payload.put("actionUrl", notification.getActionUrl());
        payload.put("createAt", notification.getCreateAt());
        // 标记为站内信推送事件，前端据此区分
        payload.put("EVENT", "NotifyPush");
        // 额外 JSON 视图，便于前端直接展示
        payload.put("raw", JSON.parseObject(JSON.toJSONString(notification)));
        return payload;
    }
}
