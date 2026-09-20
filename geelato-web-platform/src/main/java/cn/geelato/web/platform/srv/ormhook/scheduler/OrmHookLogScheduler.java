package cn.geelato.web.platform.srv.ormhook.scheduler;

import cn.geelato.core.orm.Dao;
import cn.geelato.meta.OrmHookLog;
import cn.geelato.web.platform.srv.ormhook.enums.HookLogStatusEnum;
import cn.geelato.web.platform.srv.ormhook.service.OrmHookLogProcessor;
import cn.geelato.web.platform.srv.ormhook.service.OrmHookProperties;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ORM 钩子发件箱调度器（对齐 {@code NotificationOutboxScheduler} 模式）。
 * <p>
 * 周期扫描 {@code platform_orm_hook_log} 中 status=ready（且 next_retry_at 已到期或为空）的行，
 * 委托 {@link OrmHookLogProcessor} 执行（CAS 抢占、指数退避、死信）。
 * 承担两类职责：立即执行失败后的退避重试，以及分发线程崩溃/饱和遗留行的兜底扫描。
 * <p>
 * <b>调度方式</b>：自管理 {@code ScheduledExecutorService}（守护线程），
 * 不用 {@code @Scheduled}/{@code @EnableScheduling}——后者是全局开关，会误激活平台内
 * 原本休眠的定时任务（与通知 outbox 调度器同因）。
 * <p>
 * <b>多实例部署</b>：CAS 抢占保证多实例安全（谁抢到谁执行），与总开关共用
 * {@code geelato.platform.ormhook.enabled}（默认开）。
 *
 * @author geelato
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "geelato.platform.ormhook", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrmHookLogScheduler {

    /** processing 超时回收阈值（分钟）：claim 后超此时长未完成视为执行实例崩溃残留 */
    private static final int STALE_PROCESSING_MINUTES = 10;

    private static final String TABLE = "platform_orm_hook_log";

    private final Dao dao;
    private final OrmHookProperties properties;
    private final OrmHookLogProcessor processor;

    private ScheduledExecutorService scheduler;

    @Autowired
    public OrmHookLogScheduler(@Qualifier("primaryDao") Dao dao,
                               OrmHookProperties properties,
                               OrmHookLogProcessor processor) {
        this.dao = dao;
        this.properties = properties;
        this.processor = processor;
    }

    @PostConstruct
    public void start() {
        long intervalMs = properties.getIntervalMs();
        scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "orm-hook-scheduler");
            t.setDaemon(true);
            return t;
        });
        // fixedDelay 语义：上一轮结束后等 intervalMs 再开始下一轮
        scheduler.scheduleWithFixedDelay(this::safeProcess, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        // 已完成行清理：低频，默认 6 小时一次
        if (properties.getRetentionDays() > 0) {
            long initialDelayMs = TimeUnit.MINUTES.toMillis(10);
            long periodMs = TimeUnit.HOURS.toMillis(properties.getCleanupIntervalHours());
            scheduler.scheduleWithFixedDelay(this::safeCleanup, initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
            log.info("ORM Hook 发件箱清理任务已启动，间隔 {}h，保留 {} 天",
                    properties.getCleanupIntervalHours(), properties.getRetentionDays());
        }
        log.info("ORM Hook 发件箱调度器已启动，间隔 {}ms", intervalMs);
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
            log.error("ORM Hook 发件箱调度异常：{}", t.getMessage(), t);
        }
    }

    public void process() {
        reclaimStaleProcessing();
        List<OrmHookLog> ready;
        try {
            ready = fetchReady();
        } catch (Exception e) {
            log.error("扫描 ORM Hook 发件箱失败：{}", e.getMessage(), e);
            return;
        }
        if (ready.isEmpty()) {
            return;
        }
        for (OrmHookLog row : ready) {
            try {
                processor.processOne(row);
            } catch (Exception e) {
                log.error("处理 ORM Hook 发件箱行异常 id={}, hookId={}: {}", row.getId(), row.getHookId(), e.getMessage(), e);
            }
        }
    }

    private void safeCleanup() {
        try {
            cleanupFinished();
        } catch (Throwable t) {
            log.error("ORM Hook 发件箱清理异常：{}", t.getMessage(), t);
        }
    }

    /**
     * 回收超时的 processing 行：执行实例在 claim 后崩溃会使该行永卡 processing
     * （扫描只挑 ready，无人回收），阈值远大于单次动作时长，正常执行中行不会被误伤；
     * 即使误回收导致重复执行，动作方需自带幂等（附加特性语义）。
     */
    private void reclaimStaleProcessing() {
        int reclaimed = dao.getJdbcTemplate().update(
                "UPDATE " + TABLE + " SET status = ?, update_at = ? "
                        + "WHERE status = ? AND update_at < DATE_SUB(NOW(), INTERVAL " + STALE_PROCESSING_MINUTES + " MINUTE)",
                HookLogStatusEnum.READY.value(), new Date(), HookLogStatusEnum.PROCESSING.value());
        if (reclaimed > 0) {
            log.warn("回收超时 processing 的 ORM Hook 发件箱行 {} 条（疑似执行实例崩溃，已重置为 ready 待重试）", reclaimed);
        }
    }

    /** 物理删除超过保留期的已完成（success/dead）行，避免表无限膨胀；0 表示不清理。 */
    public void cleanupFinished() {
        int retentionDays = properties.getRetentionDays();
        if (retentionDays <= 0) {
            return;
        }
        // retentionDays 为 int，直接拼字面量无注入风险；DATE_SUB 计算走数据库时间
        int deleted = dao.getJdbcTemplate().update(
                "DELETE FROM " + TABLE + " WHERE status IN (?, ?) AND update_at < DATE_SUB(NOW(), INTERVAL " + retentionDays + " DAY)",
                HookLogStatusEnum.SUCCESS.value(), HookLogStatusEnum.DEAD.value());
        if (deleted > 0) {
            log.info("清理已完成 ORM Hook 发件箱行 {} 条（保留 {} 天）", deleted, retentionDays);
        }
    }

    /** 取一批就绪项：status=ready，按 next_retry_at 升序，限制 batchSize；列显式别名为驼峰，保证 fastjson 映射到实体字段。 */
    private List<OrmHookLog> fetchReady() {
        String sql = "SELECT id, hook_id AS hookId, event_id AS eventId, entity_name AS entityName, "
                + "event_type AS eventType, op_type AS opType, action_type AS actionType, "
                + "payload_json AS payloadJson, status, retry_count AS retryCount, "
                + "next_retry_at AS nextRetryAt, error_msg AS errorMsg "
                + "FROM " + TABLE + " WHERE del_status = 0 AND status = ? "
                + "AND (next_retry_at IS NULL OR next_retry_at <= ?) "
                + "ORDER BY next_retry_at ASC, create_at ASC LIMIT ?";
        List<Map<String, Object>> rows = dao.getJdbcTemplate().queryForList(sql,
                HookLogStatusEnum.READY.value(), new Date(), properties.getBatchSize());
        List<OrmHookLog> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            list.add(JSON.parseObject(JSON.toJSONString(row), OrmHookLog.class));
        }
        return list;
    }
}
