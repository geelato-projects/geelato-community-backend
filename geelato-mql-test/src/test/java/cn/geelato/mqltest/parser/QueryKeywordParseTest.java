package cn.geelato.mqltest.parser;

import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.mqltest.support.MqlTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 第 1 层：JSON 解析层 —— 查询关键字测试。
 * <p>
 * 覆盖 @fs（字段选择）、@order（排序）、@group（分组）、@b（括号嵌套）、@p（分页）。
 */
@DisplayName("MQL 解析层：查询关键字")
class QueryKeywordParseTest extends MqlTestSupport {

    private QueryCommand parseUser(String body) {
        return parse("{\"mql_test_user\":" + body + "}");
    }

    // ==================== @fs 字段选择 ====================

    @Nested
    @DisplayName("@fs 字段选择")
    class FieldsSelect {

        @Test
        @DisplayName("单字段")
        void singleField() {
            QueryCommand cmd = parseUser("{\"@fs\":\"name\"}");
            assertArrayEquals(new String[]{"name"}, cmd.getFields());
        }

        @Test
        @DisplayName("多字段逗号分隔")
        void multipleFields() {
            QueryCommand cmd = parseUser("{\"@fs\":\"name,age,email\"}");
            assertArrayEquals(new String[]{"name", "age", "email"}, cmd.getFields());
        }

        @Test
        @DisplayName("字段别名（字段名 新名）")
        void fieldAlias() {
            QueryCommand cmd = parseUser("{\"@fs\":\"name userName\"}");
            assertArrayEquals(new String[]{"name"}, cmd.getFields());
            assertEquals("userName", cmd.getAlias().get("name"));
        }

        @Test
        @DisplayName("多字段混合别名")
        void mixedAlias() {
            QueryCommand cmd = parseUser("{\"@fs\":\"name userName,age,email\"}");
            assertEquals(3, cmd.getFields().length);
            assertEquals("userName", cmd.getAlias().get("name"));
            assertFalse(cmd.getAlias().containsKey("age"));
        }

        @Test
        @DisplayName("ref 关联字段 ref(orgId->name) orgName")
        void refForeignField() {
            QueryCommand cmd = parseUser("{\"@fs\":\"ref(orgId->name) orgName\"}");
            // ref 字段应加入 foreignFields
            assertNotNull(cmd.getForeignFields());
            assertEquals(1, cmd.getForeignFields().length);
            assertEquals("orgId->name", cmd.getForeignFields()[0]);
            assertEquals("orgName", cmd.getAlias().get("orgId->name"));
        }

        @Test
        @DisplayName("ref 关联字段（无指定远端字段，自动选展示字段）")
        void refForeignFieldNoRemote() {
            QueryCommand cmd = parseUser("{\"@fs\":\"ref(orgId) orgName\"}");
            assertNotNull(cmd.getForeignFields());
            assertEquals(1, cmd.getForeignFields().length);
        }

        @Test
        @DisplayName("@fs 与普通过滤条件共存")
        void fieldsWithWhere() {
            QueryCommand cmd = parseUser("{\"@fs\":\"name,age\",\"age|gt\":\"18\"}");
            assertArrayEquals(new String[]{"name", "age"}, cmd.getFields());
            assertEquals(1, cmd.getWhere().getFilters().size());
        }

        @Test
        @DisplayName("@fs 格式错误（3段空格）应触发校验消息")
        void fieldsInvalidFormat() {
            // "a b c" 三段格式有误，会 appendMessage，最终抛异常
            assertThrows(RuntimeException.class, () ->
                    parseUser("{\"@fs\":\"a b c\"}"));
        }
    }

    // ==================== @order 排序 ====================

    @Nested
    @DisplayName("@order 排序")
    class OrderBy {

        @Test
        @DisplayName("单字段升序 +")
        void singleAsc() {
            QueryCommand cmd = parseUser("{\"@order\":\"age|+\"}");
            assertNotNull(cmd.getOrderBy());
            assertTrue(cmd.getOrderBy().contains("age"));
            assertTrue(cmd.getOrderBy().contains("asc"));
        }

        @Test
        @DisplayName("单字段降序 -")
        void singleDesc() {
            QueryCommand cmd = parseUser("{\"@order\":\"age|-\"}");
            assertNotNull(cmd.getOrderBy());
            assertTrue(cmd.getOrderBy().contains("desc"));
        }

        @Test
        @DisplayName("多字段混合排序")
        void multiple() {
            QueryCommand cmd = parseUser("{\"@order\":\"age|-,name|+\"}");
            assertNotNull(cmd.getOrderBy());
            // 两段排序
            assertTrue(cmd.getOrderBy().contains("desc"));
            assertTrue(cmd.getOrderBy().contains("asc"));
        }

        @Test
        @DisplayName("@order 格式错误（无|分隔）应触发校验消息")
        void orderInvalidFormat() {
            assertThrows(RuntimeException.class, () ->
                    parseUser("{\"@order\":\"age\"}"));
        }

        @Test
        @DisplayName("@order 非法方向（非 +/-）应触发校验消息")
        void orderInvalidDirection() {
            assertThrows(RuntimeException.class, () ->
                    parseUser("{\"@order\":\"age|x\"}"));
        }
    }

    // ==================== @group 分组 ====================

    @Nested
    @DisplayName("@group 分组")
    class GroupBy {

        @Test
        @DisplayName("单字段分组")
        void singleField() {
            QueryCommand cmd = parse("{\"mql_test_order\":{\"@group\":\"status\"}}");
            assertEquals("status", cmd.getGroupBy());
        }

        @Test
        @DisplayName("多字段分组")
        void multipleFields() {
            QueryCommand cmd = parse("{\"mql_test_order\":{\"@group\":\"status,userId\"}}");
            assertEquals("status,userId", cmd.getGroupBy());
        }
    }

    // ==================== @p 分页 ====================

    @Nested
    @DisplayName("@p 分页")
    class Page {

        @Test
        @DisplayName("正常分页 1,10")
        void normalPage() {
            QueryCommand cmd = parseUser("{\"@p\":\"1,10\"}");
            assertEquals(1, cmd.getPageNum());
            assertEquals(10, cmd.getPageSize());
            assertTrue(cmd.isPagingQuery());
        }

        @Test
        @DisplayName("正常分页 2,20")
        void normalPage2() {
            QueryCommand cmd = parseUser("{\"@p\":\"2,20\"}");
            assertEquals(2, cmd.getPageNum());
            assertEquals(20, cmd.getPageSize());
        }

        @Test
        @DisplayName("第0页非法，应触发校验消息")
        void zeroPage() {
            assertThrows(RuntimeException.class, () ->
                    parseUser("{\"@p\":\"0,10\"}"));
        }

        @Test
        @DisplayName("负数页码非法")
        void negativePage() {
            assertThrows(RuntimeException.class, () ->
                    parseUser("{\"@p\":\"-1,10\"}"));
        }

        @Test
        @DisplayName("非数字非法")
        void nonNumericPage() {
            assertThrows(RuntimeException.class, () ->
                    parseUser("{\"@p\":\"a,10\"}"));
        }

        @Test
        @DisplayName("单值（缺页大小）非法")
        void singleValuePage() {
            assertThrows(RuntimeException.class, () ->
                    parseUser("{\"@p\":\"1\"}"));
        }
    }

    // ==================== @b 括号嵌套 ====================

    @Nested
    @DisplayName("@b 括号嵌套逻辑")
    class Brackets {

        @Test
        @DisplayName("单层 or")
        void singleOr() {
            QueryCommand cmd = parseUser("{\"@b\":[{\"or\":[{\"name|eq\":\"张三\"},{\"name|eq\":\"李四\"}]}]}");
            List<FilterGroup> children = cmd.getWhere().getChildFilterGroup();
            assertFalse(children.isEmpty());
            FilterGroup firstChild = children.get(0);
            assertEquals(FilterGroup.Logic.or, firstChild.getLogic());
            assertEquals(2, firstChild.getFilters().size());
        }

        @Test
        @DisplayName("单层 and")
        void singleAnd() {
            QueryCommand cmd = parseUser("{\"@b\":[{\"and\":[{\"name|eq\":\"张三\"},{\"age|gt\":\"18\"}]}]}");
            List<FilterGroup> children = cmd.getWhere().getChildFilterGroup();
            assertEquals(FilterGroup.Logic.and, children.get(0).getLogic());
        }

        @Test
        @DisplayName("二层嵌套 and 内含 or")
        void nestedTwoLevels() {
            QueryCommand cmd = parseUser("{\"@b\":[" +
                    "{\"and\":[" +
                    "  {\"name|eq\":\"张三\"}," +
                    "  {\"or\":[{\"age|eq\":\"18\"},{\"age|eq\":\"20\"}]}" +
                    "]}" +
                    "]}");
            List<FilterGroup> children = cmd.getWhere().getChildFilterGroup();
            FilterGroup andGroup = children.get(0);
            assertEquals(FilterGroup.Logic.and, andGroup.getLogic());
            // and 组内有1个直接 filter（name）+ 1个子组（or）
            assertFalse(andGroup.getChildFilterGroup().isEmpty());
            FilterGroup orSubGroup = andGroup.getChildFilterGroup().get(0);
            assertEquals(FilterGroup.Logic.or, orSubGroup.getLogic());
            assertEquals(2, orSubGroup.getFilters().size());
        }

        @Test
        @DisplayName("默认 or（不指定 and/or 键时默认 or）")
        void defaultOr() {
            // 只有 or 键时是 or
            QueryCommand cmd = parseUser("{\"@b\":[{\"or\":[{\"name|eq\":\"x\"}]}]}");
            assertEquals(FilterGroup.Logic.or,
                    cmd.getWhere().getChildFilterGroup().get(0).getLogic());
        }

        @Test
        @DisplayName("@b 与顶层条件共存")
        void bracketsWithTopFilters() {
            QueryCommand cmd = parseUser("{\"name|eq\":\"张三\",\"@b\":[{\"or\":[{\"age|eq\":\"18\"},{\"age|eq\":\"20\"}]}]}");
            // 顶层有1个 filter + 1个子组
            assertEquals(1, cmd.getWhere().getFilters().size());
            assertEquals(1, cmd.getWhere().getChildFilterGroup().size());
        }
    }
}
