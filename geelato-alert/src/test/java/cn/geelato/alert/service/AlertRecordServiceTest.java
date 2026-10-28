package cn.geelato.alert.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AlertRecordService 单元测试（可离线完成的纯逻辑：scope→状态集映射）。
 *
 * 覆盖场景（不触碰数据库与会话上下文）：
 * - statusSetForScope：HISTORY 归投递终态集；ACTIVE 归待聚合/待投递集；缺省返回 null（不施加 status 过滤）；大小写不敏感。
 * 显式 status 精确过滤在 pageQuery 内优先短路，不经过本方法。
 */
class AlertRecordServiceTest {

    @Test
    @DisplayName("statusSetForScope：HISTORY → 投递终态集（SENT/PARTIAL/FAIL/SILENCED）")
    void historyScopeMapsToDeliveredSet() {
        assertArrayEquals(new String[]{"SENT", "PARTIAL", "FAIL", "SILENCED"},
                AlertRecordService.statusSetForScope("HISTORY"));
    }

    @Test
    @DisplayName("statusSetForScope：大小写不敏感（history）")
    void historyScopeCaseInsensitive() {
        assertArrayEquals(new String[]{"SENT", "PARTIAL", "FAIL", "SILENCED"},
                AlertRecordService.statusSetForScope("history"));
    }

    @Test
    @DisplayName("statusSetForScope：ACTIVE → 待聚合/待投递集（PENDING/READY）")
    void activeScopeMapsToPendingSet() {
        assertArrayEquals(new String[]{"PENDING", "READY"},
                AlertRecordService.statusSetForScope("ACTIVE"));
    }

    @Test
    @DisplayName("statusSetForScope：缺省（null）返回 null，保持现有行为不加 status 过滤")
    void nullScopeReturnsNoFilter() {
        assertNull(AlertRecordService.statusSetForScope(null));
    }

    @Test
    @DisplayName("messageProduced：SENT/PARTIAL 视为已产生消息，其余否则")
    void messageProducedReflectsDeliveredStatus() {
        assertTrue(AlertRecordService.messageProduced("SENT"));
        assertTrue(AlertRecordService.messageProduced("partial"));
        assertFalse(AlertRecordService.messageProduced("PENDING"));
        assertFalse(AlertRecordService.messageProduced("READY"));
        assertFalse(AlertRecordService.messageProduced("SILENCED"));
        assertFalse(AlertRecordService.messageProduced(null));
    }
}
