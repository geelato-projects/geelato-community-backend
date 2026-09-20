package cn.geelato.web.platform.srv.ormhook.spi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钩子动作执行结果单元测试：fail 永不返回空消息（死信/重试文案依赖）。
 */
class OrmHookActionResultTest {

    @Test
    void successHasNoErrorMessage() {
        OrmHookActionResult result = OrmHookActionResult.success();
        assertTrue(result.isSuccess());
        assertNull(result.getErrorMessage());
    }

    @Test
    void failKeepsReason() {
        OrmHookActionResult result = OrmHookActionResult.fail("connection refused");
        assertFalse(result.isSuccess());
        assertEquals("connection refused", result.getErrorMessage());
    }

    @Test
    void failNeverHasBlankMessage() {
        assertEquals("unknown error", OrmHookActionResult.fail(null).getErrorMessage());
        assertEquals("unknown error", OrmHookActionResult.fail("").getErrorMessage());
        assertEquals("unknown error", OrmHookActionResult.fail("   ").getErrorMessage());
    }
}
