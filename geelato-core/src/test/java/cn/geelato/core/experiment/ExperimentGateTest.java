package cn.geelato.core.experiment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 实验功能开关判定测试：构建期白名单 && 请求级声明。
 */
class ExperimentGateTest {

    @AfterEach
    void clearContext() {
        ExperimentContext.clear();
    }

    @Test
    void disabledWhenNoRequestContext() {
        // 无请求上下文（定时任务/异步线程）：实验默认关
        assertFalse(ExperimentGate.isEnabled(ExperimentFeatures.SEARCH));
    }

    @Test
    void enabledWhenAllowedAndDeclared() {
        ExperimentContext.enable(List.of("search"));
        assertTrue(ExperimentGate.isEnabled(ExperimentFeatures.SEARCH));
    }

    @Test
    void caseInsensitiveFeatureName() {
        ExperimentContext.enable(List.of("SEARCH"));
        assertTrue(ExperimentGate.isEnabled(ExperimentFeatures.SEARCH));

        ExperimentContext.clear();
        ExperimentContext.enable(List.of("Search"));
        assertTrue(ExperimentGate.isEnabled(ExperimentFeatures.SEARCH));
    }

    @Test
    void notAllowedFeatureNeverEnabled() {
        // 白名单外功能名：header 声明了也不生效
        ExperimentContext.enable(List.of("not-registered"));
        assertFalse(ExperimentGate.isEnabled("not-registered"));
        // 且不影响白名单内功能
        assertFalse(ExperimentGate.isEnabled(ExperimentFeatures.SEARCH));
    }

    @Test
    void declaredButNotAllowedFeatureIsIgnored() {
        // Filter 层过滤后的场景：上下文只含白名单内功能
        ExperimentContext.enable(List.of("search", "future-feature"));
        assertTrue(ExperimentGate.isEnabled("search"));
        assertFalse(ExperimentGate.isEnabled("future-feature"));
    }

    @Test
    void nullAndBlankFeatureName() {
        assertFalse(ExperimentGate.isEnabled(null));
        assertFalse(ExperimentGate.isEnabled(""));
        assertFalse(ExperimentGate.isEnabled("  "));
    }

    @Test
    void clearResetsRequestContext() {
        ExperimentContext.enable(List.of("search"));
        ExperimentContext.clear();
        assertFalse(ExperimentGate.isEnabled(ExperimentFeatures.SEARCH));
        assertEquals(java.util.Set.of(), ExperimentContext.current());
    }
}
