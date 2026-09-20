package cn.geelato.archive.engine.trigger;

import cn.geelato.archive.config.ArchiveProperties;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 归档调度器：每日全局配置时刻串行执行启用策略——<b>默认关闭、按需启动</b>。
 *
 * <p><b>运行形态</b>：不常驻。默认（geelato.archive.scheduler-enabled=false）不创建任何调度线程，
 * 引擎日常静默（手动触发接口始终可用）；需要定时归档时通过配置随应用启动，或运行时
 * POST /archive/scheduler/start 随时启动（内存态，重启后回到配置默认），stop 随时停止。</p>
 *
 * <p><b>调度方式</b>：启动后自管理 {@link ScheduledExecutorService}（守护线程），照
 * NotificationOutboxScheduler 惯例——不用 @Scheduled + @EnableScheduling（平台宿主
 * 刻意未开启全局调度开关）。fixedRate 周期 24h，初始延迟对齐 schedule-time（默认 02:00，
 * 已过则次日）；异常兜底防止调度线程中断。</p>
 */
@Slf4j
public class ArchiveScheduler {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ArchiveTrigger trigger;
    private final ArchiveProperties properties;
    private final Object lifecycleLock = new Object();
    private ScheduledExecutorService scheduler;
    /** 下一次触发时刻（epoch millis），供状态查询 */
    private final AtomicLong nextFireAtMillis = new AtomicLong(0);

    public ArchiveScheduler(ArchiveTrigger trigger, ArchiveProperties properties) {
        this.trigger = trigger;
        this.properties = properties;
    }

    /** 启动定时调度（幂等：已在运行则仅刷新状态并返回） */
    public void start() {
        synchronized (lifecycleLock) {
            if (scheduler != null && !scheduler.isShutdown()) {
                log.info("归档调度器已在运行中，下次执行：{}", formatNextFireAt());
                return;
            }
            LocalTime scheduleTime = properties.getScheduleTime();
            scheduler = Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "geelato-archive-scheduler");
                t.setDaemon(true);
                return t;
            });
            long initialDelayMs = millisUntilNext(scheduleTime);
            long periodMs = TimeUnit.HOURS.toMillis(24);
            nextFireAtMillis.set(System.currentTimeMillis() + initialDelayMs);
            scheduler.scheduleAtFixedRate(this::safeRunDaily, initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
            log.info("归档调度器已启动：每日 {} 执行（首跑 {} 分钟后）", scheduleTime,
                    Duration.ofMillis(initialDelayMs).toMinutes());
        }
    }

    /** 停止定时调度（完成本次触发后不再继续；手动触发不受影响） */
    public void stop() {
        synchronized (lifecycleLock) {
            if (scheduler == null || scheduler.isShutdown()) {
                return;
            }
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            nextFireAtMillis.set(0);
            log.info("归档调度器已停止（手动触发不受影响，可随时再次启动）");
        }
    }

    public boolean isRunning() {
        ScheduledExecutorService current = scheduler;
        return current != null && !current.isShutdown();
    }

    /** 下一次触发时刻（epoch millis；未运行返回 0） */
    public long getNextFireAtMillis() {
        return isRunning() ? nextFireAtMillis.get() : 0;
    }

    public String formatNextFireAt() {
        long at = getNextFireAtMillis();
        if (at <= 0) {
            return "-";
        }
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(at), java.time.ZoneId.systemDefault())
                .format(TIME_FORMAT);
    }

    private void safeRunDaily() {
        // 每轮结束顺延 24h（fixedRate 语义）；异常兜底防止调度线程中断
        try {
            trigger.runDuePolicies();
        } catch (Throwable t) {
            log.error("归档调度异常：{}", t.getMessage(), t);
        } finally {
            nextFireAtMillis.addAndGet(TimeUnit.HOURS.toMillis(24));
        }
    }

    /** 距下一次执行时刻的毫秒数：今天该时刻已过则顺延至明天 */
    static long millisUntilNext(LocalTime scheduleTime) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.with(scheduleTime);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return Duration.between(now, next).toMillis();
    }
}
