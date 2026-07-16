package cn.geelato.mqltest.parser;

import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.parser.JsonTextQueryParser;
import cn.geelato.mqltest.support.MqlTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 第 1 层：JSON 解析层 —— 子实体、批量解析、非法输入、多关键字组合。
 */
@DisplayName("MQL 解析层：子实体/批量/非法输入/组合")
class AdvancedParseTest extends MqlTestSupport {

    // ==================== ~ 子实体查询 ====================

    @Nested
    @DisplayName("~ 子实体查询")
    class SubEntity {

        @Test
        @DisplayName("单个子实体 ~mql_test_order")
        void singleSubEntity() {
            QueryCommand cmd = parse("{\"mql_test_user\":{\"~mql_test_order\":{\"userId|eq\":\"1\"}}}");
            assertTrue(cmd.hasCommands());
            assertEquals(1, cmd.getCommands().size());
            QueryCommand sub = cmd.getCommands().get(0);
            assertEquals("mql_test_order", sub.getEntityName());
        }

        @Test
        @DisplayName("多个子实体")
        void multipleSubEntities() {
            QueryCommand cmd = parse("{\"mql_test_user\":{" +
                    "\"~mql_test_order\":{\"userId|eq\":\"1\"}," +
                    "\"~mql_test_order_item\":{\"orderId|eq\":\"2\"}" +
                    "}}");
            assertEquals(2, cmd.getCommands().size());
        }
    }

    // ==================== parseMulti 批量解析 ====================

    @Nested
    @DisplayName("parseMulti 批量解析")
    class MultiParse {

        @Test
        @DisplayName("2 实体并行查询")
        void twoEntities() {
            JsonTextQueryParser parser = new JsonTextQueryParser();
            String json = "[" +
                    "{\"mql_test_user\":{\"name|eq\":\"张三\"}}," +
                    "{\"mql_test_order\":{\"status|eq\":\"pending\"}}" +
                    "]";
            List<QueryCommand> cmds = parser.parseMulti(json);
            assertEquals(2, cmds.size());
            assertEquals("mql_test_user", cmds.get(0).getEntityName());
            assertEquals("mql_test_order", cmds.get(1).getEntityName());
        }

        @Test
        @DisplayName("3 实体并行查询")
        void threeEntities() {
            JsonTextQueryParser parser = new JsonTextQueryParser();
            String json = "[" +
                    "{\"mql_test_org\":{}}," +
                    "{\"mql_test_user\":{}}," +
                    "{\"mql_test_order\":{}}" +
                    "]";
            List<QueryCommand> cmds = parser.parseMulti(json);
            assertEquals(3, cmds.size());
        }

        @Test
        @DisplayName("parseMulti 多根元素校验（每元素必须只有1个根key）")
        void multiRootValidation() {
            JsonTextQueryParser parser = new JsonTextQueryParser();
            // 单个元素有2个根key，应触发校验
            assertThrows(RuntimeException.class, () ->
                    parser.parseMulti("[{\"a\":{},\"b\":{}}]"));
        }
    }

    // ==================== 非法输入 ====================

    @Nested
    @DisplayName("非法输入")
    class InvalidInput {

        @Test
        @DisplayName("非法 JSON 文本")
        void invalidJson() {
            assertThrows(Exception.class, () -> parse("not a json"));
        }

        @Test
        @DisplayName("多根元素（超过1个根key）")
        void multipleRootKeys() {
            assertThrows(RuntimeException.class, () ->
                    parse("{\"mql_test_user\":{},\"mql_test_order\":{}}"));
        }

        @Test
        @DisplayName("未知 @关键字")
        void unknownKeyword() {
            assertThrows(RuntimeException.class, () ->
                    parse("{\"mql_test_user\":{\"@unknown\":\"value\"}}"));
        }

        @Test
        @DisplayName("不存在的实体名")
        void nonexistentEntity() {
            assertThrows(RuntimeException.class, () ->
                    parse("{\"nonexistent_entity\":{}}"));
        }

        @Test
        @DisplayName("不存在的字段名")
        void nonexistentField() {
            assertThrows(RuntimeException.class, () ->
                parse("{\"mql_test_user\":{\"nonexistentField|eq\":\"x\"}}"));
        }

        @Test
        @DisplayName("@p 过滤符格式非法（3段）")
        void filterTooManySegments() {
            assertThrows(RuntimeException.class, () ->
                    parse("{\"mql_test_user\":{\"name|eq|x\":\"张三\"}}"));
        }

        @Test
        @DisplayName("空 JSON 对象")
        void emptyJson() {
            assertThrows(RuntimeException.class, () -> parse("{}"));
        }

        @Test
        @DisplayName("空体实体查询（合法，select *）")
        void emptyBody() {
            QueryCommand cmd = parse("{\"mql_test_user\":{}}");
            assertEquals("mql_test_user", cmd.getEntityName());
            assertNotNull(cmd.getWhere());
        }
    }

    // ==================== 多关键字组合 ====================

    @Nested
    @DisplayName("多关键字组合")
    class CombinedKeywords {

        @Test
        @DisplayName("@fs + where + @order + @p 全组合")
        void allCombined() {
            QueryCommand cmd = parse("{\"mql_test_user\":{" +
                    "\"@fs\":\"name,age\"," +
                    "\"age|gt\":\"18\"," +
                    "\"@order\":\"age|-\",\"@p\":\"1,10\"" +
                    "}}");
            assertArrayEquals(new String[]{"name", "age"}, cmd.getFields());
            assertEquals(1, cmd.getWhere().getFilters().size());
            assertNotNull(cmd.getOrderBy());
            assertEquals(1, cmd.getPageNum());
            assertEquals(10, cmd.getPageSize());
        }

        @Test
        @DisplayName("@fs + @group + @order 组合")
        void selectGroupOrder() {
            QueryCommand cmd = parse("{\"mql_test_order\":{" +
                    "\"@fs\":\"status,userId\"," +
                    "\"@group\":\"status,userId\"," +
                    "\"@order\":\"status|+\"" +
                    "}}");
            assertEquals("status,userId", cmd.getGroupBy());
            assertNotNull(cmd.getOrderBy());
        }

        @Test
        @DisplayName("@fs + @b 嵌套逻辑 + @p 组合")
        void fieldsBracketsPage() {
            QueryCommand cmd = parse("{\"mql_test_user\":{" +
                    "\"@fs\":\"name\"," +
                    "\"@b\":[{\"or\":[{\"age|eq\":\"18\"},{\"age|eq\":\"20\"}]}]," +
                    "\"@p\":\"2,5\"" +
                    "}}");
            assertEquals(1, cmd.getWhere().getChildFilterGroup().size());
            assertEquals(2, cmd.getPageNum());
            assertEquals(5, cmd.getPageSize());
        }
    }
}
