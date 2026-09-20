package cn.geelato.web.platform.srv.ormhook.scheduler;

import cn.geelato.core.orm.Dao;
import cn.geelato.web.platform.srv.ormhook.enums.HookLogStatusEnum;
import cn.geelato.web.platform.srv.ormhook.service.OrmHookLogProcessor;
import cn.geelato.web.platform.srv.ormhook.service.OrmHookProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ORM 钩子发件箱<b>兜底</b>调度器（低频，非投递路径）。
 * <p>
 * 正常执行与失败重试均为事件驱动（afterCommit 立即执行 + 内存精确定时重试，
 * 见 {@link OrmHookLogProcessor}），<b>本调度器不做高频轮询</b>，只承担两类恢复职责：
 * <ol>
 *   <li>启动立即 {@code sweep()} 一次：恢复重启前遗留（丢失的内存重试定时、崩溃卡死的 processing）</li>
 *   <li>低频周期 {@code sweep()}（默认 5 分钟，{@code geelato.platform.ormhook.sweep-interval-ms}）：
 *       恢复多实例部署下他实例故障遗留行；需要更快恢复速度可调小</li>
 * </ol>
 * 另保留已完成行（success/dead）的低频清理（默认 6 小时一次、保留 7 天）。
 * CAS 抢占保证多实例安全（谁抢到谁执行）。
 * <p>
 * <b>调度方式</b>：自管理 {@code ScheduledExecutorService}（守护线程），
 * 不用 {@code @Scheduled}/{@code @EnableScheduling}——后者是全局开关，会误激活平台内
 * 原本休眠的定时任务（与通知 outbox 调度器同因）。
 *
 * @author geelato
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "geelato.platform.ormhook", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrmHookLogScheduler {

    private static final String LOG_TABLE = "platform_orm_hook_log";

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
        long sweepIntervalMs = properties.getSweepIntervalMs();
        scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "orm-hook-scheduler");
            t.setDaemon(true);
            return t;
        });
        // 启动立即兜底一次：恢复重启前遗留（内存重试定时随进程丢失，行仍在库中 ready）
        scheduler.execute(this::safeSweep);
        // 低频周期兜底：恢复多实例他实例故障行；正常路径不依赖本扫描
        scheduler.scheduleWithFixedDelay(this::safeSweep, sweepIntervalMs, sweepIntervalMs, TimeUnit.MILLISECONDS);
        log.info("ORM Hook 兜底调度器已启动（启动即扫一次，之后间隔 {}ms；正常执行与重试为事件驱动，无高频轮询）", sweepIntervalMs);
        // 已完成行清理：低频，默认 6 小时一次
        if (properties.getRetentionDays() > 0) {
            long initialDelayMs = TimeUnit.MINUTES.toMillis(10);
            long periodMs = TimeUnit.HOURS.toMillis(properties.getCleanupIntervalHours());
            scheduler.scheduleWithFixedDelay(this::safeCleanup, initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
            log.info("ORM Hook 发件箱清理任务已启动，间隔 {}h，保留 {} 天",
                    properties.getCleanupIntervalHours(), properties.getRetentionDays());
        }
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

    private void safeSweep() {
        try {
            processor.sweep();
        } catch (Throwable t) {
            // 兜底：任何异常都不能让调度线程中断（scheduleWithFixedDelay 遇异常会停止后续调度）
            log.error("ORM Hook 发件箱兜底扫描异常：{}", t.getMessage(), t);
        }
    }

    /** 手动触发一次兜底扫描（排障/运维用）。 */
    public void sweepNow() {
        processor.sweep();
    }

    /** 物理删除超过保留期的已完成（success/dead）行，避免表无限膨胀；0 表示不清理。 */
    public void cleanupFinished() {
        int retentionDays = properties.getRetentionDays();
        if (retentionDays <= 0) {
            return;
        }
        // retentionDays 为 int，直接拼字面量无注入风险；DATE_SUB 计算走数据库时间
        int deleted = dao.getJdbcTemplate().update(
                "DELETE FROM " + LOG_TABLE + " WHERE status IN (?, ?) AND update_at < DATE_SUB(NOW(), INTERVAL " + retentionDays + " DAY)",
                HookLogStatusEnum.SUCCESS.value(), HookLogStatusEnum.DEAD.value());
        if (deleted > 0) {
            log.info("清理已完成 ORM Hook 发件箱行 {} 条（保留 {} 天）", deleted, retentionDays);
        }
    }

    private void safeCleanup() {
        try {
            cleanupFinished();
        } catch (Throwable t) {
            log.error("ORM Hook 发件箱清理异常：{}", t.getMessage(), t);
        }
    }
}
