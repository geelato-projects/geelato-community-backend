package cn.geelato.alert.engine;

import cn.geelato.alert.boot.AlertEngineProperties;
import cn.geelato.alert.engine.deliver.DeliverEngine;
import cn.geelato.alert.engine.scan.AlertSourceScanner;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 引擎调度器（自管理 ScheduledExecutorService，不用 @Scheduled——平台宿主刻意不开全局调度）：
 * 主 tick 串行跑 源扫描→冲刷→投递→REPEAT；schedule tick 独立跑 SCHEDULE cron 轮询。
 */
@Slf4j
public class AlertEngineScheduler {

    private final AlertSourceScanner sourceScanner;
    private final FlushScanner flushScanner;
    private final DeliverEngine deliverEngine;
    private final RepeatScanner repeatScanner;
    private final ScheduleRunner scheduleRunner;
    private final AlertEngineProperties properties;

    private ScheduledExecutorService ticker;

    public AlertEngineScheduler(AlertSourceScanner sourceScanner, FlushScanner flushScanner,
                                DeliverEngine deliverEngine, RepeatScanner repeatScanner,
                                ScheduleRunner scheduleRunner, AlertEngineProperties properties) {
        this.sourceScanner = sourceScanner;
        this.flushScanner = flushScanner;
        this.deliverEngine = deliverEngine;
        this.repeatScanner = repeatScanner;
        this.scheduleRunner = scheduleRunner;
        this.properties = properties;
    }

    @PostConstruct
    public void start() {
        ticker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "alert-engine-scheduler");
            t.setDaemon(true);
            return t;
        });
        int tick = Math.max(1, properties.getTickSeconds());
        int scheduleTick = Math.max(5, properties.getScheduleTickSeconds());
        ticker.scheduleWithFixedDelay(this::safeMainTick, tick, tick, TimeUnit.SECONDS);
        ticker.scheduleWithFixedDelay(this::safeScheduleTick, scheduleTick, scheduleTick, TimeUnit.SECONDS);
        log.info("告警引擎已启动 mainTick={}s scheduleTick={}s", tick, scheduleTick);
    }

    private void safeMainTick() {
        try {
            sourceScanner.tick();
            flushScanner.tick();
            deliverEngine.deliver();
            repeatScanner.tick();
        } catch (Exception e) {
            log.warn("告警引擎主 tick 异常", e);
        }
    }

    private void safeScheduleTick() {
        try {
            scheduleRunner.tick();
        } catch (Exception e) {
            log.warn("告警引擎 schedule tick 异常", e);
        }
    }

    @PreDestroy
    public void stop() {
        if (ticker != null) {
            ticker.shutdownNow();
            log.info("告警引擎已停止");
        }
    }
}
