package cn.geelato.archive.engine.trigger;

import cn.geelato.archive.config.ArchiveProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 调度时刻计算与生命周期（默认静默、start 幂等、stop 可重启）测试 */
class ArchiveSchedulerTest {

    /** 构造不触发真实归档的调度器：trigger 传 null（首跑在 24h 之后，测试内不会触发） */
    private ArchiveScheduler newScheduler() {
        return new ArchiveScheduler(null, new ArchiveProperties());
    }

    @Test
    void futureTimeToday() {
        LocalTime future = LocalTime.now().plusMinutes(10);
        long ms = ArchiveScheduler.millisUntilNext(future);
        // 未来 10 分钟的时刻，应落在 (0, 10min] 内
        assertTrue(ms > 0 && ms <= 10 * 60 * 1000L, "actual=" + ms);
    }

    @Test
    void passedTimeRollsToTomorrow() {
        LocalTime past = LocalTime.now().minusMinutes(10);
        long ms = ArchiveScheduler.millisUntilNext(past);
        // 已过 10 分钟的时刻 → 明天该时刻：约 24h - 10min
        long dayMs = 24 * 60 * 60 * 1000L;
        assertTrue(ms > dayMs - 15 * 60 * 1000L && ms <= dayMs, "actual=" + ms);
    }

    @Test
    void alwaysPositive() {
        assertTrue(ArchiveScheduler.millisUntilNext(LocalTime.MIDNIGHT) > 0);
        assertTrue(ArchiveScheduler.millisUntilNext(LocalTime.of(23, 59, 59)) > 0);
    }

    @Test
    void defaultIdleWithoutThread() {
        ArchiveScheduler scheduler = newScheduler();
        assertFalse(scheduler.isRunning());
        assertEquals(0, scheduler.getNextFireAtMillis());
        assertEquals("-", scheduler.formatNextFireAt());
    }

    @Test
    void startIsIdempotentAndStopAllowsRestart() {
        ArchiveScheduler scheduler = newScheduler();
        try {
            scheduler.start();
            assertTrue(scheduler.isRunning());
            assertTrue(scheduler.getNextFireAtMillis() > System.currentTimeMillis());
            // 重复 start 幂等：仍在运行，不重建
            scheduler.start();
            assertTrue(scheduler.isRunning());
        } finally {
            scheduler.stop();
        }
        assertFalse(scheduler.isRunning());
        assertEquals(0, scheduler.getNextFireAtMillis());
        // stop 后可再次启动（随时 run 起来）
        scheduler.start();
        try {
            assertTrue(scheduler.isRunning());
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void stopWhenIdleIsNoop() {
        ArchiveScheduler scheduler = newScheduler();
        scheduler.stop();
        assertFalse(scheduler.isRunning());
    }
}

