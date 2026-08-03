package cn.geelato.web.platform.srv.notification.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.meta.Notification;
import cn.geelato.meta.NotificationOutbox;
import cn.geelato.web.platform.srv.notification.channel.DeliveryChannel;
import cn.geelato.web.platform.srv.notification.channel.DeliveryChannelManager;
import cn.geelato.web.platform.srv.notification.config.NotificationProperties;
import cn.geelato.web.platform.srv.notification.dto.ChannelResult;
import cn.geelato.web.platform.srv.notification.enums.OutboxStatusEnum;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 通知投递 outbox 调度器。
 * <p>
 * 周期扫描 platform_notification_outbox 中 status=ready（且 next_retry_at 已到期或为空）的记录，
 * CAS 抢占为 processing（单实例安全），按 channel 路由到 {@link DeliveryChannel} 执行投递：
 * <ul>
 *   <li>成功 → success</li>
 *   <li>失败且未达上限 → 仍置 ready，retryCount+1，next_retry_at = 指数退避（下次扫描重试）</li>
 *   <li>失败且达上限 → dead（死信）</li>
 * </ul>
 * 单渠道失败不影响其他渠道（每渠道独立 outbox 行）。
 *
 * @author geelato
 */
@Component
@Slf4j
public class NotificationOutboxScheduler {

    private final Dao dao;
    private final NotificationProperties properties;
    private final DeliveryChannelManager channelManager;

    @Autowired
    public NotificationOutboxScheduler(@Qualifier("primaryDao") Dao dao,
                                       NotificationProperties properties,
                                       DeliveryChannelManager channelManager) {
        this.dao = dao;
        this.properties = properties;
        this.channelManager = channelManager;
    }

    @Scheduled(fixedDelayString = "${geelato.notification.outbox.interval-ms:3000}")
    public void process() {
        List<NotificationOutbox> ready;
        try {
            ready = fetchReady();
        } catch (Exception e) {
            log.error("扫描通知 outbox 失败：{}", e.getMessage(), e);
            return;
        }
        if (ready == null || ready.isEmpty()) {
            return;
        }
        for (NotificationOutbox outbox : ready) {
            try {
                processOne(outbox);
            } catch (Exception e) {
                log.error("处理 outbox 项异常 id={}, channel={}: {}", outbox.getId(), outbox.getChannel(), e.getMessage(), e);
                markForRetryOrDead(outbox, e.getMessage());
            }
        }
    }

    /** 取一批就绪项：status=ready，按 next_retry_at 升序，限制 batchSize */
    @SuppressWarnings("unchecked")
    private List<NotificationOutbox> fetchReady() {
        String sql = "SELECT * FROM platform_notification_outbox WHERE del_status = 0 AND status = ? "
                + "AND (next_retry_at IS NULL OR next_retry_at <= ?) "
                + "ORDER BY next_retry_at ASC, create_at ASC LIMIT ?";
        List<Map<String, Object>> rows = dao.getJdbcTemplate().queryForList(sql,
                OutboxStatusEnum.READY.value(), new Date(), properties.getOutboxBatchSize());
        List<NotificationOutbox> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            list.add(JSON.parseObject(JSON.toJSONString(row), NotificationOutbox.class));
        }
        return list;
    }

    private void processOne(NotificationOutbox outbox) {
        // 1. CAS 抢占 ready -> processing（单实例下防止重复处理）
        if (!claim(outbox.getId())) {
            return;
        }
        // 2. 取主体
        Notification notification = dao.queryForObject(Notification.class, outbox.getNotificationId());
        if (notification == null) {
            log.warn("通知主体不存在，outbox 置死信：notificationId={}", outbox.getNotificationId());
            markDead(outbox, "通知主体不存在");
            return;
        }
        // 3. 解析收件人
        List<String> userIds = parseRecipients(outbox.getRecipientJson());
        if (userIds.isEmpty()) {
            markSuccess(outbox);
            return;
        }
        // 4. 路由渠道
        DeliveryChannel channel = resolveChannel(outbox.getChannel());
        if (channel == null) {
            markDead(outbox, "无可用的投递渠道实现：" + outbox.getChannel());
            return;
        }
        // 5. 执行投递
        ChannelResult result = channel.deliver(notification, userIds);
        if (result != null && result.isSuccess()) {
            markSuccess(outbox);
        } else {
            String err = result != null ? result.getErrorMessage() : "投递返回 null";
            markForRetryOrDead(outbox, err);
        }
    }

    /** 解析投递渠道：按 outbox.channel 精确匹配已注册的 DeliveryChannel 实现 */
    private DeliveryChannel resolveChannel(String channel) {
        return channelManager.getChannel(channel);
    }

    /** CAS 抢占：UPDATE status=processing WHERE id=? AND status=ready，返回是否抢占成功 */
    private boolean claim(String id) {
        int n = dao.getJdbcTemplate().update(
                "UPDATE platform_notification_outbox SET status = ?, update_at = ? WHERE id = ? AND status = ?",
                OutboxStatusEnum.PROCESSING.value(), new Date(), id, OutboxStatusEnum.READY.value());
        return n > 0;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseRecipients(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.parseArray(json, String.class);
        } catch (Exception e) {
            log.warn("解析收件人 JSON 失败：{}", json, e);
            return List.of();
        }
    }

    private void markSuccess(NotificationOutbox outbox) {
        dao.getJdbcTemplate().update(
                "UPDATE platform_notification_outbox SET status = ?, update_at = ? WHERE id = ?",
                OutboxStatusEnum.SUCCESS.value(), new Date(), outbox.getId());
    }

    private void markDead(NotificationOutbox outbox, String reason) {
        dao.getJdbcTemplate().update(
                "UPDATE platform_notification_outbox SET status = ?, error_msg = ?, update_at = ? WHERE id = ?",
                OutboxStatusEnum.DEAD.value(), truncate(reason, 500), new Date(), outbox.getId());
        log.error("通知投递进入死信：notificationId={}, channel={}, reason={}",
                outbox.getNotificationId(), outbox.getChannel(), reason);
    }

    /** 失败：未达上限则重试（ready + 退避），达上限则死信 */
    private void markForRetryOrDead(NotificationOutbox outbox, String reason) {
        int newRetry = outbox.getRetryCount() + 1;
        if (newRetry >= properties.getMaxRetryCount()) {
            markDead(outbox, "达到最大重试次数(" + properties.getMaxRetryCount() + ")：" + reason);
            return;
        }
        // 指数退避：max(30s, 2^retry * 60s)
        long backoffMs = Math.max(30_000L, (1L << newRetry) * 60_000L);
        Date nextRetry = new Date(System.currentTimeMillis() + backoffMs);
        dao.getJdbcTemplate().update(
                "UPDATE platform_notification_outbox SET status = ?, retry_count = ?, next_retry_at = ?, error_msg = ?, update_at = ? WHERE id = ?",
                OutboxStatusEnum.READY.value(), newRetry, nextRetry, truncate(reason, 500), new Date(), outbox.getId());
        log.warn("通知投递失败，将重试({}/{}) notificationId={}, channel={}, nextRetryAt={}, reason={}",
                newRetry, properties.getMaxRetryCount(), outbox.getNotificationId(), outbox.getChannel(), nextRetry, reason);
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
