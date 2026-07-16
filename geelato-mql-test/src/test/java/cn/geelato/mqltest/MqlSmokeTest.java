package cn.geelato.mqltest;

import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.mqltest.support.MqlSqlAssertions;
import cn.geelato.mqltest.support.MqlTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Types;

/**
 * 基础设施冒烟测试 —— 验证 MqlTestSupport + MqlSqlAssertions 能正常工作。
 * 这是后续大规模测试用例的基础保障。
 */
@DisplayName("MQL 基础设施冒烟测试")
class MqlSmokeTest extends MqlTestSupport {

    @Test
    @DisplayName("空查询：select * from mql_test_user")
    void emptyQuery() {
        QueryCommand cmd = parse("{\"mql_test_user\":{}}");
        BoundSql sql = generateSql(cmd);

        MqlSqlAssertions.assertSqlEquals("select * from mql_test_user", sql);
        MqlSqlAssertions.assertParams(new Object[0], sql);
        MqlSqlAssertions.assertTypes(new int[0], sql);
    }

    @Test
    @DisplayName("eq 操作符：name = ?")
    void whereEq() {
        QueryCommand cmd = parse("{\"mql_test_user\":{\"name|eq\":\"张三\"}}");
        BoundSql sql = generateSql(cmd);

        // 注意：WHERE 条件中的普通字段名不加反引号（tryAppendKeywords 只对保留字 index/inner/enable/key 加引号）
        // 操作符两侧无空格：ConditionOperator 直接拼接 name + "=" + "?"
        MqlSqlAssertions.assertSqlEquals(
                "select * from mql_test_user where name=?", sql);
        MqlSqlAssertions.assertParams(new Object[]{"张三"}, sql);
        MqlSqlAssertions.assertTypes(new int[]{Types.VARCHAR}, sql);
    }

    @Test
    @DisplayName("默认操作符（省略eq）：name = ?")
    void whereDefaultOperator() {
        QueryCommand cmd = parse("{\"mql_test_user\":{\"name\":\"张三\"}}");
        BoundSql sql = generateSql(cmd);

        MqlSqlAssertions.assertSqlEquals(
                "select * from mql_test_user where name=?", sql);
        MqlSqlAssertions.assertParams(new Object[]{"张三"}, sql);
    }

    @Test
    @DisplayName("分页查询：limit offset + countSql")
    void pagination() {
        QueryCommand cmd = parse("{\"mql_test_user\":{\"@p\":\"1,10\"}}");
        var bps = generatePageSql(cmd);

        MqlSqlAssertions.assertSqlContains("limit 10 offset 0", bps.getBoundSql());
        MqlSqlAssertions.assertSqlContains("select count(*) from (", bps.getCountSql());
    }
}
