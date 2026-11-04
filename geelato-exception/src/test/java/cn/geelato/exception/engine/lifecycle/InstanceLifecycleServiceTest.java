package cn.geelato.exception.engine.lifecycle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * InstanceLifecycleService 单元测试（离线纯逻辑：终身一条/重开模式的状态集判定）。
 *
 * 覆盖场景（不触碰数据库）：
 * - isActiveStatus：在途集 OPEN/PROCESSING/ESCALATED（指纹持有态，续期分支，ESCALATED 再命中不再提交失败）；
 * - isReopenableStatus：暂时关闭集 RESOLVED/IGNORED（重开分支）。
 */
class InstanceLifecycleServiceTest {

    @Test
    @DisplayName("isActiveStatus：OPEN/PROCESSING/ESCALATED 为在途（含 ESCALATED，再命中走续期）")
    void activeStatusIncludesEscalated() {
        assertTrue(InstanceLifecycleService.isActiveStatus("OPEN"));
        assertTrue(InstanceLifecycleService.isActiveStatus("PROCESSING"));
        assertTrue(InstanceLifecycleService.isActiveStatus("ESCALATED"));
    }

    @Test
    @DisplayName("isActiveStatus：终态与非空值不在在途集")
    void activeStatusExcludesClosedAndUnknown() {
        assertFalse(InstanceLifecycleService.isActiveStatus("RESOLVED"));
        assertFalse(InstanceLifecycleService.isActiveStatus("IGNORED"));
        assertFalse(InstanceLifecycleService.isActiveStatus(null));
        assertFalse(InstanceLifecycleService.isActiveStatus(""));
    }

    @Test
    @DisplayName("isReopenableStatus：RESOLVED/IGNORED 为可重开的暂时关闭态")
    void reopenableStatusIsClosedSet() {
        assertTrue(InstanceLifecycleService.isReopenableStatus("RESOLVED"));
        assertTrue(InstanceLifecycleService.isReopenableStatus("IGNORED"));
        // 在途态不可重开（走续期分支）；升级态只能人工收口，不进重开分支
        assertFalse(InstanceLifecycleService.isReopenableStatus("OPEN"));
        assertFalse(InstanceLifecycleService.isReopenableStatus("PROCESSING"));
        assertFalse(InstanceLifecycleService.isReopenableStatus("ESCALATED"));
        assertFalse(InstanceLifecycleService.isReopenableStatus(null));
    }
}
