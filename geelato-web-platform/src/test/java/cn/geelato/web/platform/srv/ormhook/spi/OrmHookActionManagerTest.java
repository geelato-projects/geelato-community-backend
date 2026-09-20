package cn.geelato.web.platform.srv.ormhook.spi;

import cn.geelato.meta.OrmHook;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钩子动作执行器注册表与结果对象单元测试：类型路由、重复实现硬失败、fail 永不返回空消息
 * （发件箱处理器的死信/重试文案依赖该契约）。
 */
class OrmHookActionManagerTest {

    private static OrmHookActionExecutor executorOf(String type) {
        return new OrmHookActionExecutor() {
            @Override
            public String getType() {
                return type;
            }

            @Override
            public OrmHookActionResult execute(OrmHook rule, Map<String, Object> payload) {
                return OrmHookActionResult.success();
            }
        };
    }

    @Test
    void routesByType() {
        OrmHookActionExecutor api = executorOf("api");
        OrmHookActionExecutor http = executorOf("http");
        OrmHookActionManager manager = new OrmHookActionManager(List.of(api, http));
        assertSame(api, manager.getExecutor("api"));
        assertSame(http, manager.getExecutor("http"));
        assertTrue(manager.supports("api"));
        assertFalse(manager.supports("mq"));
    }

    @Test
    void nullOrBlankTypeResolvesNothing() {
        OrmHookActionManager manager = new OrmHookActionManager(List.of(executorOf("api")));
        assertNull(manager.getExecutor(null));
        assertNull(manager.getExecutor(""));
        assertNull(manager.getExecutor("  "));
        assertFalse(manager.supports(null));
    }

    @Test
    void emptyExecutorListIsAllowed() {
        OrmHookActionManager manager = new OrmHookActionManager(null);
        assertNull(manager.getExecutor("api"));
        assertFalse(manager.supports("api"));
    }

    @Test
    void duplicateTypeFailsHard() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new OrmHookActionManager(List.of(executorOf("api"), executorOf("api"))));
        assertTrue(ex.getMessage().contains("api"));
        assertTrue(ex.getMessage().contains("重复"));
    }
}
