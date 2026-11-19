package cn.geelato.core;

import cn.geelato.core.enums.SecurityLevelEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 系统密级的环境变量解析（GEELATO_SECURITY_LEVEL）：null/空白默认 2；0-10 整数合法；
 * 非数字与越界启动期即失败。匿名通道开关（GEELATO_ANONYMOUS_ENABLED）三态解析与回落判定。
 */
class GlobalContextSecurityLevelTest {

    @Test
    void nullOrBlankDefaultsToStandard() {
        assertEquals(2, GlobalContext.resolveSecurityLevel(null));
        assertEquals(2, GlobalContext.resolveSecurityLevel(""));
        assertEquals(2, GlobalContext.resolveSecurityLevel("   "));
    }

    @Test
    void parsesValidLevels() {
        assertEquals(0, GlobalContext.resolveSecurityLevel("0"));
        assertEquals(1, GlobalContext.resolveSecurityLevel("1"));
        assertEquals(2, GlobalContext.resolveSecurityLevel("2"));
        assertEquals(3, GlobalContext.resolveSecurityLevel("3"));
        assertEquals(10, GlobalContext.resolveSecurityLevel("10"));
        assertEquals(7, GlobalContext.resolveSecurityLevel(" 7 "));
    }

    @Test
    void nonNumericRejected() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> GlobalContext.resolveSecurityLevel("high"));
        assertTrue(ex.getMessage().contains("GEELATO_SECURITY_LEVEL"));
        assertTrue(ex.getMessage().contains("high"));
    }

    @Test
    void outOfRangeRejected() {
        IllegalStateException low = assertThrows(IllegalStateException.class,
                () -> GlobalContext.resolveSecurityLevel("-1"));
        assertTrue(low.getMessage().contains("GEELATO_SECURITY_LEVEL"));
        assertThrows(IllegalStateException.class, () -> GlobalContext.resolveSecurityLevel("11"));
    }

    @Test
    void unsetEnvironmentResolvesToStandard() {
        // 测试进程未设置 GEELATO_SECURITY_LEVEL，固化值应为默认 2
        assertEquals(2, GlobalContext.getSecurityLevel());
        assertEquals(SecurityLevelEnum.STANDARD, GlobalContext.getSecurityLevelOption());
        assertTrue(GlobalContext.getColumnEncryptOption());
        assertTrue(GlobalContext.getApiEncryptOption());
    }

    @Test
    void anonymousUnsetFallsBackToLevelDecision() {
        assertNull(GlobalContext.resolveAnonymousEnabled(null));
        assertNull(GlobalContext.resolveAnonymousEnabled(""));
        assertNull(GlobalContext.resolveAnonymousEnabled("   "));
        // 测试进程未设置 GEELATO_ANONYMOUS_ENABLED，密级 2 时匿名通道随密级判定开启
        assertNull(GlobalContext.resolveAnonymousEnabled(System.getenv("GEELATO_ANONYMOUS_ENABLED")));
        assertTrue(GlobalContext.getAnonymousOption());
    }

    @Test
    void anonymousExplicitValueParses() {
        assertEquals(true, GlobalContext.resolveAnonymousEnabled("true"));
        assertEquals(false, GlobalContext.resolveAnonymousEnabled("false"));
        assertEquals(true, GlobalContext.resolveAnonymousEnabled("TRUE"));
        assertEquals(false, GlobalContext.resolveAnonymousEnabled(" False "));
    }

    @Test
    void anonymousIllegalValueRejected() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> GlobalContext.resolveAnonymousEnabled("yes"));
        assertTrue(ex.getMessage().contains("GEELATO_ANONYMOUS_ENABLED"));
        assertTrue(ex.getMessage().contains("yes"));
    }
}
