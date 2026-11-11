package cn.geelato.web.platform.srv.mv;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MvSqlRewriterTest {

    private static final Map<String, String> MAPPING = Map.of(
            "orders", "gl_1_db_orders",
            "customers", "gl_1_db_customers");
    private static final Set<String> QUALIFIERS = Set.of("shop");

    @Test
    void rewritesBareTableRefs() {
        String sql = "select o.id, c.name from orders o join customers c on o.customer_id = c.id where o.tenant_code = ?";
        String expected = "select o.id, c.name from gl_1_db_orders o join gl_1_db_customers c on o.customer_id = c.id where o.tenant_code = ?";
        assertEquals(expected, MvSqlRewriter.rewrite(sql, MAPPING, QUALIFIERS));
    }

    @Test
    void matchingIsCaseInsensitive() {
        assertEquals("FROM gl_1_db_orders", MvSqlRewriter.rewrite("FROM ORDERS", MAPPING, QUALIFIERS));
    }

    @Test
    void stringLiteralsAreSkipped() {
        String sql = "select 'orders' as src, \"customers\" as dst from orders";
        String expected = "select 'orders' as src, \"customers\" as dst from gl_1_db_orders";
        assertEquals(expected, MvSqlRewriter.rewrite(sql, MAPPING, QUALIFIERS));
    }

    @Test
    void backslashEscapeInsideStringDoesNotEndLiteral() {
        String sql = "select 'it\\'s orders' from orders";
        String expected = "select 'it\\'s orders' from gl_1_db_orders";
        assertEquals(expected, MvSqlRewriter.rewrite(sql, MAPPING, QUALIFIERS));
    }

    @Test
    void commentsAreSkipped() {
        String sql = "select 1 -- from orders\nfrom /* customers */ orders";
        String expected = "select 1 -- from orders\nfrom /* customers */ gl_1_db_orders";
        assertEquals(expected, MvSqlRewriter.rewrite(sql, MAPPING, QUALIFIERS));
    }

    @Test
    void backtickIdentifiersAreReplaced() {
        assertEquals("from gl_1_db_orders o", MvSqlRewriter.rewrite("from `orders` o", MAPPING, QUALIFIERS));
        // 未映射的反引号标识符去引号小写（RW 不认反引号）
        assertEquals("from not_mapped", MvSqlRewriter.rewrite("from `Not_Mapped`", MAPPING, QUALIFIERS));
    }

    @Test
    void tableQualifiedColumnReplaced() {
        String sql = "select orders.id, customers.name from orders join customers on orders.customer_id = customers.id";
        String expected = "select gl_1_db_orders.id, gl_1_db_customers.name from gl_1_db_orders join gl_1_db_customers on gl_1_db_orders.customer_id = gl_1_db_customers.id";
        assertEquals(expected, MvSqlRewriter.rewrite(sql, MAPPING, QUALIFIERS));
    }

    @Test
    void schemaQualifiedTableReplaced() {
        assertEquals("from shop.gl_1_db_orders",
                MvSqlRewriter.rewrite("from shop.orders", MAPPING, QUALIFIERS));
    }

    @Test
    void aliasQualifiedColumnNamedAsTableNotReplaced() {
        // customers 表别名 c；c.orders 是列名，不得替换
        String sql = "select c.orders from customers c join orders on orders.id = c.orders";
        String expected = "select c.orders from gl_1_db_customers c join gl_1_db_orders on gl_1_db_orders.id = c.orders";
        assertEquals(expected, MvSqlRewriter.rewrite(sql, MAPPING, QUALIFIERS));
    }

    @Test
    void identifierBeforeParenNotReplaced() {
        // 函数名与表名同名时不替换
        assertEquals("select orders() from gl_1_db_orders",
                MvSqlRewriter.rewrite("select orders() from orders", MAPPING, QUALIFIERS));
    }

    @Test
    void unknownSqlUnchanged() {
        String sql = "select a.id from audit_log a where a.del_status = 0";
        assertEquals(sql, MvSqlRewriter.rewrite(sql, MAPPING, QUALIFIERS));
    }

    @Test
    void emptyMappingReturnsInput() {
        String sql = "select * from orders";
        assertEquals(sql, MvSqlRewriter.rewrite(sql, Map.of(), QUALIFIERS));
        assertEquals(sql, MvSqlRewriter.rewrite(sql, null, null));
    }

    @Test
    void withoutQualifiersAliasColumnStillSafe() {
        // 不传库限定符时，点后的表名（别名.列）一律不动
        String sql = "select c.orders from customers c";
        String expected = "select c.orders from gl_1_db_customers c";
        assertEquals(expected, MvSqlRewriter.rewrite(sql, MAPPING));
    }
}
