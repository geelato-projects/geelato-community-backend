package cn.geelato.web.platform.srv.mv;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MvViewExpanderTest {

    @Test
    void extractsFromAndJoinReferences() {
        String sql = "select o.id, c.name from orders o join customers c on o.customer_id = c.id where o.tenant_code = ?";
        assertEquals(List.of("orders", "customers"), MvViewExpander.extractReferences(sql));
    }

    @Test
    void skipsCommentsAndStringsButKeepsSubqueryReferences() {
        // 子查询内的物理表同样是依赖，需要摄取；字符串与注释中的引用跳过
        String sql = "select * from (select id from t_inner) x, 'orders' -- from fake\n join /* from fake2 */ real_table";
        assertEquals(List.of("t_inner", "real_table"), MvViewExpander.extractReferences(sql));
    }

    @Test
    void takesObjectPartOfQualifiedName() {
        assertEquals(List.of("orders"), MvViewExpander.extractReferences("select * from shop.orders"));
    }

    @Test
    void excludesSqlKeywordsAfterFrom() {
        // from ( 由子查询分支处理；left join 的 left 不应被当作表名
        String sql = "select 1 from t_a left join t_b on t_a.id = t_b.id";
        assertEquals(List.of("t_a", "t_b"), MvViewExpander.extractReferences(sql));
    }

    @Test
    void deduplicatesAndKeepsOrder() {
        assertEquals(List.of("t_a", "t_b"), MvViewExpander.extractReferences("from t_a join t_b, t_a"));
    }

    @Test
    void emptySqlReturnsEmpty() {
        assertEquals(List.of(), MvViewExpander.extractReferences(null));
        assertEquals(List.of(), MvViewExpander.extractReferences(""));
    }

    @Test
    void normalizesMysqlBackticksAndCaseForRw() {
        // SHOW CREATE VIEW 返回的 MySQL 方言：反引号 + 原大小写 → RW 小写无反引号；字符串字面量原样
        String def = "select `COLUMNA`, `COLUMNB` from `demo_mt_view` where `name` = 'demo `x`'";
        String normalized = MvViewExpander.normalizeMysqlSql(def);
        assertEquals("select columna, columnb from demo_mt_view where name = 'demo `x`'", normalized);
    }

    @Test
    void plainIdentifierKeepsSpecialCharsQuoted() {
        assertEquals("order_items", MvSqlRewriter.plainIdentifier("Order_Items"));
        assertEquals("\"order items\"", MvSqlRewriter.plainIdentifier("Order Items"));
    }
}
