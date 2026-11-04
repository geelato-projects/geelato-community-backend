package cn.geelato.mqltest.sql;

import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.mqltest.support.MqlSqlAssertions;
import cn.geelato.mqltest.support.MqlTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Types;

import static cn.geelato.mqltest.support.MqlSqlAssertions.assertSqlEquals;
import static cn.geelato.mqltest.support.MqlSqlAssertions.assertParams;
import static cn.geelato.mqltest.support.MqlSqlAssertions.assertTypes;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 第 2 层：SQL 生成层 —— WHERE 条件 14 个操作符的 SQL 片段。
 * <p>
 * 断言 {@link BoundSql} 的 sql + params + types 三同步。
 * 这是 MQL 最复杂、最容易出 bug 的部分。
 */
@DisplayName("MQL SQL 生成：WHERE 操作符")
class ConditionOperatorSqlTest extends MqlTestSupport {

    private BoundSql genUser(String body) {
        return generateSql(parse("{\"mql_test_user\":" + body + "}"));
    }

    // ==================== 比较操作符 ====================

    @Nested
    @DisplayName("比较操作符 eq/neq/lt/lte/gt/gte")
    class Comparison {

        @Test
        void eq() {
            BoundSql sql = genUser("{\"name|eq\":\"张三\"}");
            assertSqlEquals("select * from mql_test_user where name=?", sql);
            assertParams(new Object[]{"张三"}, sql);
            assertTypes(new int[]{Types.VARCHAR}, sql);
        }

        @Test
        void neq() {
            BoundSql sql = genUser("{\"name|neq\":\"李四\"}");
            assertSqlEquals("select * from mql_test_user where name!=?", sql);
            assertParams(new Object[]{"李四"}, sql);
        }

        @Test
        void lt() {
            BoundSql sql = genUser("{\"age|lt\":\"18\"}");
            assertSqlEquals("select * from mql_test_user where age<?", sql);
            assertParams(new Object[]{"18"}, sql);
            assertTypes(new int[]{Types.INTEGER}, sql);
        }

        @Test
        void lte() {
            BoundSql sql = genUser("{\"age|lte\":\"18\"}");
            assertSqlEquals("select * from mql_test_user where age<=?", sql);
        }

        @Test
        void gt() {
            BoundSql sql = genUser("{\"age|gt\":\"18\"}");
            assertSqlEquals("select * from mql_test_user where age>?", sql);
        }

        @Test
        void gte() {
            BoundSql sql = genUser("{\"age|gte\":\"18\"}");
            assertSqlEquals("select * from mql_test_user where age>=?", sql);
        }
    }

    // ==================== 模糊匹配操作符 ====================

    @Nested
    @DisplayName("模糊匹配 startwith/endwith/contains")
    class Like {

        @Test
        void startwith() {
            BoundSql sql = genUser("{\"name|startwith\":\"张\"}");
            assertSqlEquals("select * from mql_test_user where name like CONCAT('',?,'%')", sql);
            assertParams(new Object[]{"张"}, sql);
            // like 类一律按字符串绑定
            assertTypes(new int[]{Types.VARCHAR}, sql);
        }

        @Test
        void endwith() {
            BoundSql sql = genUser("{\"name|endwith\":\"三\"}");
            assertSqlEquals("select * from mql_test_user where name like CONCAT('%',?,'')", sql);
        }

        @Test
        void contains() {
            BoundSql sql = genUser("{\"name|contains\":\"三\"}");
            assertSqlEquals("select * from mql_test_user where name like CONCAT('%',?,'%')", sql);
        }
    }

    // ==================== 集合操作符 ====================

    @Nested
    @DisplayName("集合 in/nin")
    class InNin {

        @Test
        void in() {
            BoundSql sql = genUser("{\"id|in\":\"1,2,3\"}");
            assertSqlEquals("select * from mql_test_user where id in(?,?,?)", sql);
            assertParams(new Object[]{"1", "2", "3"}, sql);
            assertTypes(new int[]{Types.BIGINT, Types.BIGINT, Types.BIGINT}, sql);
        }

        @Test
        void nin() {
            BoundSql sql = genUser("{\"id|nin\":\"4,5\"}");
            assertSqlEquals("select * from mql_test_user where id not in(?,?)", sql);
            assertParams(new Object[]{"4", "5"}, sql);
        }
    }

    // ==================== 空值检查 ====================

    @Nested
    @DisplayName("空值 nil")
    class Nil {

        @Test
        void nil_isNull() {
            BoundSql sql = genUser("{\"email|nil\":\"1\"}");
            // nil 不产生参数
            assertSqlEquals("select * from mql_test_user where email is NULL", sql);
            assertParams(new Object[0], sql);
            assertTypes(new int[0], sql);
        }

        @Test
        void nil_isNotNull() {
            BoundSql sql = genUser("{\"email|nil\":\"0\"}");
            assertSqlEquals("select * from mql_test_user where email is NOT NULL", sql);
            assertParams(new Object[0], sql);
        }
    }

    // ==================== 区间 between ====================

    @Nested
    @DisplayName("区间 bt")
    class Between {

        @Test
        void bt() {
            BoundSql sql = genUser("{\"age|bt\":\"[18,60]\"}");
            // bt 直接拼入SQL文本（非参数化）
            assertSqlEquals("select * from mql_test_user where age between '18' and '60'", sql);
            assertParams(new Object[0], sql);
            assertTypes(new int[0], sql);
        }
    }

    // ==================== FIND_IN_SET ====================

    @Nested
    @DisplayName("集合查找 fis")
    class FindInSet {

        @Test
        void fis_singleValue() {
            BoundSql sql = genUser("{\"name|fis\":\"vip\"}");
            // fis 生成 FIND_IN_SET( '值',字段) >0（>0 前有空格）
            assertSqlEquals("select * from mql_test_user where ( FIND_IN_SET( 'vip',name) >0 )", sql);
            assertParams(new Object[0], sql);
        }

        @Test
        void fis_multiValue() {
            BoundSql sql = genUser("{\"name|fis\":\"vip,svip\"}");
            MqlSqlAssertions.assertSqlContains("FIND_IN_SET( 'vip',name) >0", sql);
            MqlSqlAssertions.assertSqlContains("or", sql);
            MqlSqlAssertions.assertSqlContains("FIND_IN_SET( 'svip',name) >0", sql);
        }
    }

    // ==================== JSON 列特殊处理 ====================

    @Nested
    @DisplayName("JSON 列操作")
    class JsonColumn {

        @Test
        void fis_onJsonColumn_generatesJsonContains() {
            // tags 是 JSON 列，fis 自动转 JSON_CONTAINS
            BoundSql sql = generateSql(parse("{\"mql_test_order\":{\"tags|fis\":\"red\"}}"));
            MqlSqlAssertions.assertSqlContains("JSON_CONTAINS", sql);
            MqlSqlAssertions.assertSqlContains("tags", sql);
            // fis 不产生参数
            assertParams(new Object[0], sql);
        }

        @Test
        void fis_multiValue_onJsonColumn() {
            BoundSql sql = generateSql(parse("{\"mql_test_order\":{\"tags|fis\":\"red,blue\"}}"));
            MqlSqlAssertions.assertSqlContains("JSON_CONTAINS", sql);
            MqlSqlAssertions.assertSqlContains("or", sql);
        }

        @Test
        void eq_onJsonColumn_throwsBecauseParamsNotSupported() {
            // JSON 列 eq 在 SQL 片段生成时会转 JSON_CONTAINS，但参数绑定阶段
            // recombine 会断言 JSON 列不支持 eq（Assert.isTrue），抛 IllegalArgumentException。
            // 这验证了真实约束：JSON 列只能用 fis，不能用 eq。
            assertThrows(IllegalArgumentException.class, () ->
                    generateSql(parse("{\"mql_test_order\":{\"tags|eq\":\"red\"}}")));
        }

        @Test
        void lt_onJsonColumn_throwsBecauseParamsNotSupported() {
            assertThrows(IllegalArgumentException.class, () ->
                    generateSql(parse("{\"mql_test_order\":{\"tags|lt\":\"red\"}}")));
        }
    }

    // ==================== 多条件 AND ====================

    @Test
    @DisplayName("多条件默认 AND 连接")
    void multipleConditionsAnd() {
        BoundSql sql = genUser("{\"name|eq\":\"张三\",\"age|gt\":\"18\"}");
        assertSqlEquals("select * from mql_test_user where name=? and age>?", sql);
        assertParams(new Object[]{"张三", "18"}, sql);
        assertTypes(new int[]{Types.VARCHAR, Types.INTEGER}, sql);
    }

    // ==================== @b 嵌套逻辑 SQL ====================

    @Test
    @DisplayName("@b or 嵌套生成括号（@b 必须与顶层条件共存才生成 WHERE）")
    void bracketsOr() {
        // 注意：当前实现中 WHERE 子句仅在顶层 filters 非空时生成，
        // 单独使用 @b（childFilterGroup）不会生成 WHERE。需配合顶层 filter。
        BoundSql sql = genUser("{\"age|gt\":\"0\",\"@b\":[{\"or\":[{\"name|eq\":\"张三\"},{\"name|eq\":\"李四\"}]}]}");
        // 子组用 or 连接，生成 and ( name=? or name=? )
        MqlSqlAssertions.assertSqlContains("where age>?", sql);
        MqlSqlAssertions.assertSqlContains("( name=? or name=? )", sql);
        assertParams(new Object[]{"0", "张三", "李四"}, sql);
    }
}
