package cn.geelato.mqltest.cases;

import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.execute.BoundSql;
import cn.geelato.mqltest.support.MqlSqlAssertions;
import cn.geelato.mqltest.support.MqlTestCaseLoader;
import cn.geelato.mqltest.support.MqlTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static cn.geelato.mqltest.support.MqlSqlAssertions.assertSqlEquals;
import static cn.geelato.mqltest.support.MqlSqlAssertions.assertParams;
import static cn.geelato.mqltest.support.MqlSqlAssertions.assertTypes;

/**
 * 参数化测试：从 mql-test-cases/*.json 加载声明式用例。
 * <p>
 * 新增用例只需在 JSON 文件中追加条目，无需修改 Java 代码。
 * 这是可持续扩充测试覆盖的主要入口。
 */
@DisplayName("MQL 参数化用例（声明式）")
class ParameterizedMqlTest extends MqlTestSupport {

    /**
     * 加载 where-operators.json 的全部用例作为参数源。
     */
    static Stream<Arguments> whereOperatorCases() {
        List<MqlTestCaseLoader.MqlTestCase> cases = MqlTestCaseLoader.load("where-operators.json");
        return cases.stream().map(Arguments::of);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("whereOperatorCases")
    @DisplayName("WHERE 操作符参数化用例")
    void whereOperatorCases(MqlTestCaseLoader.MqlTestCase testCase) {
        QueryCommand cmd = parse(testCase.getMql());
        BoundSql sql = generateSql(cmd);

        if (testCase.getExpectedSql() != null) {
            assertSqlEquals(testCase.getExpectedSql(), sql);
        }
        if (testCase.getExpectedParams() != null) {
            assertParams(testCase.getExpectedParams(), sql);
        }
        if (testCase.getExpectedTypes() != null) {
            assertTypes(testCase.getExpectedTypes(), sql);
        }
    }
}
