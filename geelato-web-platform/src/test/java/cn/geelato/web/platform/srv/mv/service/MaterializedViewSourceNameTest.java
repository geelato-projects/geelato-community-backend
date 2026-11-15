package cn.geelato.web.platform.srv.mv.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CDC source 共享命名规则测试：同一源连接 + 同一源库共享一个 source（按视图隔离为历史旧命名）。
 */
class MaterializedViewSourceNameTest {

    @Test
    @DisplayName("基本命名：gl_{连接id}_{库名}，非法字符替换为 _，整体小写")
    void basicNaming() {
        assertEquals("gl_conn01_ordersdb", MaterializedViewService.sourceName("conn01", "ordersDb"));
        assertEquals("gl_conn_id_01_my_db", MaterializedViewService.sourceName("conn:id-01", "my-db"));
        assertEquals("gl_conn01_", MaterializedViewService.sourceName("conn01", null));
    }

    @Test
    @DisplayName("同一连接同一库的不同视图共享同名 source（视图名不参与命名）")
    void sharedAcrossViews() {
        String byViewA = MaterializedViewService.sourceName("conn01", "db1");
        String byViewB = MaterializedViewService.sourceName("conn01", "db1");
        assertEquals(byViewA, byViewB);
    }

    @Test
    @DisplayName("不同库或不同连接 → 不同 source 名")
    void distinctByDatabaseOrConnect() {
        assertNotEquals(MaterializedViewService.sourceName("conn01", "db1"),
                MaterializedViewService.sourceName("conn01", "db2"));
        assertNotEquals(MaterializedViewService.sourceName("conn01", "db1"),
                MaterializedViewService.sourceName("conn02", "db1"));
    }

    @Test
    @DisplayName("超 63 字符截断加哈希后缀，结果确定且合法")
    void longNameTruncatedWithHash() {
        String connectId = "a".repeat(70);
        String database = "b".repeat(70);
        String first = MaterializedViewService.sourceName(connectId, database);
        String second = MaterializedViewService.sourceName(connectId, database);
        assertEquals(first, second);
        assertTrue(first.length() <= 63, "截断后长度不应超过 63：" + first.length());
        assertTrue(first.matches("^[a-z][a-z0-9_]*$"), "应为合法标识符：" + first);
    }
}
