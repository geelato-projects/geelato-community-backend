package cn.geelato.web.platform.srv.ormhook.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 钩子事件类型枚举单元测试：of 解析契约（注册表加载与写入校验均依赖）。
 */
class OrmHookEventEnumTest {

    @Test
    void ofMapsAllValidValues() {
        assertEquals(OrmHookEventEnum.INSERT, OrmHookEventEnum.of("insert"));
        assertEquals(OrmHookEventEnum.UPDATE, OrmHookEventEnum.of("update"));
        assertEquals(OrmHookEventEnum.DELETE, OrmHookEventEnum.of("delete"));
    }

    @Test
    void ofReturnsNullForInvalidValues() {
        assertNull(OrmHookEventEnum.of(null));
        assertNull(OrmHookEventEnum.of(""));
        assertNull(OrmHookEventEnum.of("  "));
        assertNull(OrmHookEventEnum.of("select"));
        assertNull(OrmHookEventEnum.of("after_save_commit"));
        assertNull(OrmHookEventEnum.of("INSERT"));
    }

    @Test
    void valuesRoundTripWithStorage() {
        for (OrmHookEventEnum event : OrmHookEventEnum.values()) {
            assertNotNull(OrmHookEventEnum.of(event.value()));
            assertEquals(event, OrmHookEventEnum.of(event.value()));
        }
    }
}
