package cn.geelato.mqltest.sql;

import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.core.mql.execute.BoundPageSql;
import cn.geelato.mqltest.support.MqlSqlAssertions;
import cn.geelato.mqltest.support.MqlTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static cn.geelato.mqltest.support.MqlSqlAssertions.assertSqlContains;
import static cn.geelato.mqltest.support.MqlSqlAssertions.assertSqlEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 第 2 层：SQL 生成层 —— SELECT / FROM / JOIN / ORDER / GROUP / 分页 / count。
 */
@DisplayName("MQL SQL 生成：SELECT/JOIN/ORDER/分页")
class SelectJoinPageSqlTest extends MqlTestSupport {

    private BoundSql genUser(String body) {
        return generateSql(parse("{\"mql_test_user\":" + body + "}"));
    }

    // ==================== SELECT 字段 ====================

    @Nested
    @DisplayName("SELECT 子句")
    class SelectClause {

        @Test
        @DisplayName("无 @fs：select *")
        void selectAll() {
            BoundSql sql = genUser("{}");
            assertSqlEquals("select * from mql_test_user", sql);
        }

        @Test
        @DisplayName("单字段：select 列名")
        void selectSingleField() {
            BoundSql sql = genUser("{\"@fs\":\"name\"}");
            // SELECT 字段用列名，有别名则输出 fieldName
            assertSqlContains("select name", sql);
        }

        @Test
        @DisplayName("多字段逗号分隔")
        void selectMultipleFields() {
            BoundSql sql = genUser("{\"@fs\":\"name,age\"}");
            assertSqlContains("name", sql);
            assertSqlContains("age", sql);
        }

        @Test
        @DisplayName("字段别名（字段名 新名）")
        void selectFieldWithAlias() {
            BoundSql sql = genUser("{\"@fs\":\"name userName\"}");
            // name 列输出为 name userName（列名 别名）
            assertSqlContains("name", sql);
            assertSqlContains("userName", sql);
        }
    }

    // ==================== JOIN 外键关联 ====================

    @Nested
    @DisplayName("JOIN 外键关联")
    class JoinClause {

        @Test
        @DisplayName("ref 关联字段触发 left join")
        void refGeneratesLeftJoin() {
            BoundSql sql = genUser("{\"@fs\":\"ref(orgId->name) orgName\"}");
            // 应生成 left join mql_test_org ... on ... org_id = ... id
            assertSqlContains("left join", sql);
            assertSqlContains("mql_test_org", sql);
            assertSqlContains("on", sql);
            assertSqlContains("org_id", sql);
        }

        @Test
        @DisplayName("ref 关联后主表有别名 t0")
        void refMainTableAlias() {
            BoundSql sql = genUser("{\"@fs\":\"ref(orgId->name) orgName\"}");
            // 有 JOIN 时主表分配别名 t0
            assertSqlContains("mql_test_user t0", sql);
        }
    }

    // ==================== ORDER BY ====================

    @Nested
    @DisplayName("ORDER BY")
    class OrderByClause {

        @Test
        @DisplayName("单字段升序")
        void orderAsc() {
            BoundSql sql = genUser("{\"@order\":\"age|+\"}");
            assertSqlContains("order by", sql);
            assertSqlContains("asc", sql);
        }

        @Test
        @DisplayName("单字段降序")
        void orderDesc() {
            BoundSql sql = genUser("{\"@order\":\"age|-\"}");
            assertSqlContains("order by", sql);
            assertSqlContains("desc", sql);
        }

        @Test
        @DisplayName("多字段排序")
        void orderMultiple() {
            BoundSql sql = genUser("{\"@order\":\"age|-,name|+\"}");
            assertSqlContains("order by", sql);
            assertSqlContains("desc", sql);
            assertSqlContains("asc", sql);
        }
    }

    // ==================== GROUP BY + HAVING ====================

    @Nested
    @DisplayName("GROUP BY")
    class GroupByClause {

        @Test
        @DisplayName("单字段分组")
        void groupSingle() {
            BoundSql sql = generateSql(parse("{\"mql_test_order\":{\"@group\":\"status\"}}"));
            assertSqlContains("group by", sql);
            assertSqlContains("status", sql);
        }

        @Test
        @DisplayName("多字段分组")
        void groupMultiple() {
            BoundSql sql = generateSql(parse("{\"mql_test_order\":{\"@group\":\"status,userId\"}}"));
            assertSqlContains("group by", sql);
            assertSqlContains("status", sql);
            assertSqlContains("userId", sql);
        }
    }

    // ==================== 分页 + count ====================

    @Nested
    @DisplayName("分页与 count")
    class Pagination {

        @Test
        @DisplayName("第1页 limit offset")
        void firstPage() {
            BoundPageSql bps = generatePageSql(parse("{\"mql_test_user\":{\"@p\":\"1,10\"}}"));
            assertSqlContains("limit 10 offset 0", bps.getBoundSql());
        }

        @Test
        @DisplayName("第3页 limit offset 计算")
        void thirdPage() {
            BoundPageSql bps = generatePageSql(parse("{\"mql_test_user\":{\"@p\":\"3,10\"}}"));
            // offset = (3-1) * 10 = 20
            assertSqlContains("limit 10 offset 20", bps.getBoundSql());
        }

        @Test
        @DisplayName("count SQL 套娃")
        void countSql() {
            BoundPageSql bps = generatePageSql(parse("{\"mql_test_user\":{\"@p\":\"1,10\"}}"));
            assertNotNull(bps.getCountSql());
            assertSqlContains("select count(*) from (", bps.getCountSql());
            assertSqlContains(") t", bps.getCountSql());
        }

        @Test
        @DisplayName("无分页时不生成 limit")
        void noPagination() {
            BoundSql sql = genUser("{\"name|eq\":\"x\"}");
            // 不含 limit
            String normalized = MqlSqlAssertions.normalize(sql.getSql());
            org.junit.jupiter.api.Assertions.assertFalse(normalized.contains("limit"),
                    "无分页不应包含 limit: " + normalized);
        }
    }
}
