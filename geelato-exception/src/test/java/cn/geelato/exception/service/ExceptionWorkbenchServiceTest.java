package cn.geelato.exception.service;

import cn.geelato.exception.engine.hit.SubmitReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ExceptionWorkbenchService 单元测试（可离线完成的纯逻辑：scope→状态集映射、重算动作汇总）。
 *
 * 覆盖场景（不触碰数据库与会话上下文）：
 * - statusSetForScope：HISTORY 归历史终态集；ACTIVE/缺省归在途集；大小写不敏感。
 * 显式 status 精确过滤在 pageQuery 内优先短路，不经过本方法。
 * - recalcActionOf：组级恢复优先于命中提交；提交报告内重开 > 续期 > 新建；
 * 冷却/抑制全跳过（计数全 0）归 NO_CHANGE。
 */
class ExceptionWorkbenchServiceTest {

    @Test
    @DisplayName("statusSetForScope：HISTORY → 历史终态集（RESOLVED/IGNORED）")
    void historyScopeMapsToResolvedSet() {
        assertArrayEquals(new Object[]{"RESOLVED", "IGNORED"},
                ExceptionWorkbenchService.statusSetForScope("HISTORY"));
    }

    @Test
    @DisplayName("statusSetForScope：大小写不敏感（history）")
    void historyScopeCaseInsensitive() {
        assertArrayEquals(new Object[]{"RESOLVED", "IGNORED"},
                ExceptionWorkbenchService.statusSetForScope("history"));
    }

    @Test
    @DisplayName("statusSetForScope：ACTIVE → 在途集（OPEN/PROCESSING/ESCALATED）")
    void activeScopeMapsToInFlightSet() {
        assertArrayEquals(new Object[]{"OPEN", "PROCESSING", "ESCALATED"},
                ExceptionWorkbenchService.statusSetForScope("ACTIVE"));
    }

    @Test
    @DisplayName("statusSetForScope：缺省（null）归在途集，保持向后兼容")
    void nullScopeDefaultsToInFlightSet() {
        assertArrayEquals(new Object[]{"OPEN", "PROCESSING", "ESCALATED"},
                ExceptionWorkbenchService.statusSetForScope(null));
    }

    @Test
    @DisplayName("messageProduced：SENT/PARTIAL 视为已产生消息，其余否则")
    void messageProducedReflectsDeliveredStatus() {
        assertTrue(ExceptionWorkbenchService.messageProduced("SENT"));
        assertTrue(ExceptionWorkbenchService.messageProduced("partial"));
        assertFalse(ExceptionWorkbenchService.messageProduced("PENDING"));
        assertFalse(ExceptionWorkbenchService.messageProduced("READY"));
        assertFalse(ExceptionWorkbenchService.messageProduced("FAIL"));
        assertFalse(ExceptionWorkbenchService.messageProduced(null));
    }

    @Test
    @DisplayName("staySeconds：在途 = now−first_at")
    void staySecondsInFlightIsNowMinusFirstAt() {
        Date now = new Date();
        Date firstAt = new Date(now.getTime() - 90 * 1000L);
        assertEquals(90L, ExceptionWorkbenchService.staySecondsOf("OPEN", firstAt, null, now));
        assertEquals(90L, ExceptionWorkbenchService.staySecondsOf("PROCESSING", firstAt, null, now));
        assertEquals(90L, ExceptionWorkbenchService.staySecondsOf("ESCALATED", firstAt, null, now));
    }

    @Test
    @DisplayName("staySeconds：已关闭 = resolved_at−first_at（关闭后不再增长）")
    void staySecondsClosedIsResolvedMinusFirstAt() {
        Date firstAt = new Date(System.currentTimeMillis() - 10 * 60 * 1000L);
        Date resolvedAt = new Date(firstAt.getTime() + 2 * 60 * 1000L);
        Date now = new Date();
        assertEquals(120L, ExceptionWorkbenchService.staySecondsOf("RESOLVED", firstAt, resolvedAt, now));
        assertEquals(120L, ExceptionWorkbenchService.staySecondsOf("IGNORED", firstAt, resolvedAt, now));
    }

    @Test
    @DisplayName("staySeconds：first_at 缺失返回 null；时钟回拨钳 0")
    void staySecondsNullWithoutFirstAtAndClampsNegative() {
        Date now = new Date();
        assertNull(ExceptionWorkbenchService.staySecondsOf("OPEN", null, null, now));
        assertNull(ExceptionWorkbenchService.staySecondsOf("RESOLVED", now, null, now));
        assertEquals(0L, ExceptionWorkbenchService.staySecondsOf(
                "OPEN", new Date(now.getTime() + 5000), null, now));
    }

    @Test
    @DisplayName("recalcActionOf：组级恢复优先（recovered>0 一律 RECOVERED）")
    void recalcActionRecoveredTakesPrecedence() {
        assertEquals("RECOVERED", ExceptionWorkbenchService.recalcActionOf(1, null));
        assertEquals("RECOVERED", ExceptionWorkbenchService.recalcActionOf(2, report(0, 1, 1)));
    }

    @Test
    @DisplayName("recalcActionOf：提交报告内重开 > 续期 > 新建")
    void recalcActionPrefersReopenOverRenewOverCreate() {
        assertEquals("REOPENED", ExceptionWorkbenchService.recalcActionOf(0, report(1, 1, 1)));
        assertEquals("RENEWED", ExceptionWorkbenchService.recalcActionOf(0, report(1, 1, 0)));
        assertEquals("CREATED", ExceptionWorkbenchService.recalcActionOf(0, report(1, 0, 0)));
    }

    @Test
    @DisplayName("recalcActionOf：无报告（不命中未恢复）或计数全 0（冷却/抑制跳过）归 NO_CHANGE")
    void recalcActionNoChangeWhenNothingHappened() {
        assertEquals("NO_CHANGE", ExceptionWorkbenchService.recalcActionOf(0, null));
        SubmitReport allSkipped = new SubmitReport();
        allSkipped.setCooldownSkipped(3);
        assertEquals("NO_CHANGE", ExceptionWorkbenchService.recalcActionOf(0, allSkipped));
    }

    private static SubmitReport report(int created, int renewed, int reopened) {
        SubmitReport r = new SubmitReport();
        r.setInstanceCreated(created);
        r.setInstanceRenewed(renewed);
        r.setInstanceReopened(reopened);
        return r;
    }
}
