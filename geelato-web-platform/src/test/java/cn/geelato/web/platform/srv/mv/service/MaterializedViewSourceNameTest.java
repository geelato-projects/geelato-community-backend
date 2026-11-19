package cn.geelato.web.platform.srv.mv.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CDC source 共享命名规则测试：同一源连接共享一个 source（库名/视图名不参与命名，按视图隔离为历史旧命名）。
 */
class MaterializedViewSourceNameTest {

    @Test
    @DisplayName("基本命名：gl_{连接id}，非法字符替换为 _，整体小写")
    void basicNaming() {
        assertEquals("gl_conn01", MaterializedViewService.sourceName("conn01"));
        assertEquals("gl_conn_id_01", MaterializedViewService.sourceName("conn:id-01"));
        assertEquals("gl_conn01", MaterializedViewService.sourceName("Conn01"));
        assertEquals("gl_", MaterializedViewService.sourceName(null));
    }

    @Test
    @DisplayName("同一连接的不同视图/不同库共享同名 source（库名与视图名不参与命名）")
    void sharedAcrossViewsAndDatabases() {
        String byViewA = MaterializedViewService.sourceName("conn01");
        String byViewB = MaterializedViewService.sourceName("conn01");
        assertEquals(byViewA, byViewB);
    }

    @Test
    @DisplayName("不同连接 → 不同 source 名")
    void distinctByConnect() {
        assertNotEquals(MaterializedViewService.sourceName("conn01"),
                MaterializedViewService.sourceName("conn02"));
    }

    @Test
    @DisplayName("超 63 字符截断加哈希后缀，结果确定且合法")
    void longNameTruncatedWithHash() {
        String connectId = "a".repeat(70);
        String first = MaterializedViewService.sourceName(connectId);
        String second = MaterializedViewService.sourceName(connectId);
        assertEquals(first, second);
        assertTrue(first.length() <= 63, "截断后长度不应超过 63：" + first.length());
        assertTrue(first.matches("^[a-z][a-z0-9_]*$"), "应为合法标识符：" + first);
    }
}