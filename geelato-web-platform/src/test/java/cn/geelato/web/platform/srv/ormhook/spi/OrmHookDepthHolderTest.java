package cn.geelato.web.platform.srv.ormhook.spi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钩子执行深度守卫单元测试：嵌套安全、异常路径还原、退出后复位
 * （监听器 supports() 的防递归判定依赖该契约）。
 */
class OrmHookDepthHolderTest {

    @AfterEach
    void cleanup() {
        // 防御：异常路径泄漏的深度标记不能影响后续用例
        OrmHookDepthHolder.call(() -> null);
    }

    @Test
    void notExecutingByDefault() {
        assertFalse(OrmHookDepthHolder.isInHookExecution());
    }

    @Test
    void marksDepthDuringExecutionAndResetsAfter() {
        String result = OrmHookDepthHolder.call(() -> {
            assertTrue(OrmHookDepthHolder.isInHookExecution());
            return "ok";
        });
        assertEquals("ok", result);
        assertFalse(OrmHookDepthHolder.isInHookExecution());
    }

    @Test
    void nestingIsSafe() {
        OrmHookDepthHolder.call(() -> {
            assertTrue(OrmHookDepthHolder.isInHookExecution());
            OrmHookDepthHolder.call(() -> {
                assertTrue(OrmHookDepthHolder.isInHookExecution());
                return null;
            });
            // 内层退出后外层深度仍生效
            assertTrue(OrmHookDepthHolder.isInHookExecution());
            return null;
        });
        assertFalse(OrmHookDepthHolder.isInHookExecution());
    }

    @Test
    void resetsAfterActionThrows() {
        assertThrows(IllegalStateException.class, () ->
                OrmHookDepthHolder.call(() -> {
                    throw new IllegalStateException("script error");
                }));
        assertFalse(OrmHookDepthHolder.isInHookExecution());
    }
}
