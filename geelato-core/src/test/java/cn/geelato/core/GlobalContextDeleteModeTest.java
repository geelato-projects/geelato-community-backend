package cn.geelato.core;

import cn.geelato.lang.meta.DeleteMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全局默认删除模式的环境变量解析（GEELATO_DELETE_MODE）：
 * null/空白默认 LOGIC；logic|physical 忽略大小写；auto 与非法值启动期即失败。
 */
class GlobalContextDeleteModeTest {

    @Test
    void nullOrBlankDefaultsToLogic() {
        assertEquals(DeleteMode.LOGIC, GlobalContext.resolveDefaultDeleteMode(null));
        assertEquals(DeleteMode.LOGIC, GlobalContext.resolveDefaultDeleteMode(""));
        assertEquals(DeleteMode.LOGIC, GlobalContext.resolveDefaultDeleteMode("   "));
    }

    @Test
    void parsesIgnoreCase() {
        assertEquals(DeleteMode.LOGIC, GlobalContext.resolveDefaultDeleteMode("logic"));
        assertEquals(DeleteMode.LOGIC, GlobalContext.resolveDefaultDeleteMode("LOGIC"));
        assertEquals(DeleteMode.PHYSICAL, GlobalContext.resolveDefaultDeleteMode("physical"));
        assertEquals(DeleteMode.PHYSICAL, GlobalContext.resolveDefaultDeleteMode("Physical"));
    }

    @Test
    void autoRejected() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> GlobalContext.resolveDefaultDeleteMode("auto"));
        assertTrue(ex.getMessage().contains("GEELATO_DELETE_MODE"));
    }

    @Test
    void illegalValueRejected() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> GlobalContext.resolveDefaultDeleteMode("yes"));
        assertTrue(ex.getMessage().contains("GEELATO_DELETE_MODE"));
        assertTrue(ex.getMessage().contains("yes"));
    }

    @Test
    void unsetEnvironmentResolvesToLogic() {
        // 测试进程未设置 GEELATO_DELETE_MODE，固化值应为默认 LOGIC
        assertEquals(DeleteMode.LOGIC, GlobalContext.getDefaultDeleteMode());
    }
}
