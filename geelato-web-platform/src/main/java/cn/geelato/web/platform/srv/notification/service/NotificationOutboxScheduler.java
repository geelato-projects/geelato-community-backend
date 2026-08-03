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
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
 * <p>
 * <b>调度方式</b>：使用自管理的 {@link ScheduledExecutorService}（守护线程），
 * 而非 Spring 的 {@code @Scheduled} + {@code @EnableScheduling}。
 * 原因：{@code @EnableScheduling} 是全局开关，会同时激活代码库里所有 {@code @Scheduled} 方法——
 * 包括原本设计为按需开启、但因全局缺 {@code @EnableScheduling} 而一直休眠的邮件 IMAP 同步任务，
 * 后者属于网络/DB 密集型操作，一旦被意外激活会拖慢整个平台。这里隔离调度，避免误伤。
 *
 * @author geelato
 */
@Component
@Slf4j
public class NotificationOutboxScheduler {

    private final Dao dao;
    private final NotificationProperties properties;
    private final DeliveryChannelManager channelManager;

    private ScheduledExecutorService scheduler;

    @Autowired
    public NotificationOutboxScheduler(@Qualifier("primaryDao") Dao dao,
                                       NotificationProperties properties,
                                       DeliveryChannelManager channelManager) {
        this.dao = dao;
        this.properties = properties;
        this.channelManager = channelManager;
    }

    @PostConstruct
    public void start() {
        long intervalMs = properties.getOutboxIntervalMs();
        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "notification-outbox-scheduler");
            t.setDaemon(true);
            return t;
        });
        // fixedDelay 语义：上一轮结束后等 intervalMs 再开始下一轮（与 @Scheduled(fixedDelay) 一致）
        scheduler.scheduleWithFixedDelay(this::safeProcess, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        // 已完成行清理：低频，默认 6 小时一次
        if (properties.getOutboxRetentionDays() > 0) {
            long cleanupIntervalHours = properties.getOutboxCleanupIntervalHours();
            long initialDelayMs = TimeUnit.MINUTES.toMillis(10); // 启动 10 分钟后首次清理
            long periodMs = TimeUnit.HOURS.toMillis(cleanupIntervalHours);
            scheduler.scheduleWithFixedDelay(this::safeCleanup, initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
            log.info("通知 outbox 清理任务已启动，间隔 {}h，保留 {} 天", cleanupIntervalHours, properties.getOutboxRetentionDays());
        }
        log.info("通知 outbox 调度器已启动，间隔 {}ms", intervalMs);
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void safeProcess() {
        try {
            process();
        } catch (Throwable t) {
            // 兜底：任何异常都不能让调度线程中断（scheduleWithFixedDelay 遇异常会停止后续调度）
            log.error("通知 outbox 调度异常：{}", t.getMessage(), t);
        }
    }

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

    private void safeCleanup() {
        try {
            cleanupFinished();
        } catch (Throwable t) {
            log.error("通知 outbox 清理异常：{}", t.getMessage(), t);
        }
    }

    /**
     * 物理删除超过保留期的已完成（success/dead）outbox 行，避免表无限膨胀。
     * 扫描只命中 status=success/dead 的行，不影响 ready/processing 投递。
     */
    public void cleanupFinished() {
        int retentionDays = properties.getOutboxRetentionDays();
        if (retentionDays <= 0) {
            return;
        }
        // retentionDays 为 int，直接拼字面量无注入风险；DATE_SUB 计算走数据库时间
        int deleted = dao.getJdbcTemplate().update(
                "DELETE FROM platform_notification_outbox "
                        + "WHERE status IN (?, ?) AND update_at < DATE_SUB(NOW(), INTERVAL " + retentionDays + " DAY)",
                OutboxStatusEnum.SUCCESS.value(), OutboxStatusEnum.DEAD.value());
        if (deleted > 0) {
            log.info("清理已完成通知 outbox 行 {} 条（保留 {} 天）", deleted, retentionDays);
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
