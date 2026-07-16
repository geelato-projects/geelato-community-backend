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
 * 第 1 层：JSON 解析层 —— 过滤操作符测试。
 * <p>
 * 覆盖全部 14 个操作符：eq/neq/lt/lte/gt/gte/startwith/endwith/contains/in/nin/nil/bt/fis
 * 以及默认操作符（省略 eq）、字段引用语法。
 */
@DisplayName("MQL 解析层：过滤操作符")
class FilterOperatorParseTest extends MqlTestSupport {

    private QueryCommand parseUser(String body) {
        return parse("{\"mql_test_user\":" + body + "}");
    }

    private FilterGroup.Filter firstFilter(QueryCommand cmd) {
        List<FilterGroup.Filter> filters = cmd.getWhere().getFilters();
        assertFalse(filters.isEmpty(), "应有至少一个过滤条件");
        return filters.get(0);
    }

    // ==================== 默认操作符（省略） ====================

    @Test
    @DisplayName("省略操作符默认为 eq")
    void defaultOperatorIsEq() {
        QueryCommand cmd = parseUser("{\"name\":\"张三\"}");
        FilterGroup.Filter f = firstFilter(cmd);
        assertEquals(FilterGroup.Operator.eq, f.getOperator());
        assertEquals("name", f.getField());
        assertEquals("张三", f.getValue());
    }

    // ==================== 14 个操作符逐一测试 ====================

    @Test
    @DisplayName("eq 操作符")
    void operatorEq() {
        QueryCommand cmd = parseUser("{\"name|eq\":\"张三\"}");
        FilterGroup.Filter f = firstFilter(cmd);
        assertEquals(FilterGroup.Operator.eq, f.getOperator());
        assertEquals("张三", f.getValue());
    }

    @Test
    @DisplayName("neq 操作符")
    void operatorNeq() {
        QueryCommand cmd = parseUser("{\"name|neq\":\"李四\"}");
        assertEquals(FilterGroup.Operator.neq, firstFilter(cmd).getOperator());
    }

    @Test
    @DisplayName("lt 操作符")
    void operatorLt() {
        QueryCommand cmd = parseUser("{\"age|lt\":\"18\"}");
        assertEquals(FilterGroup.Operator.lt, firstFilter(cmd).getOperator());
    }

    @Test
    @DisplayName("lte 操作符")
    void operatorLte() {
        QueryCommand cmd = parseUser("{\"age|lte\":\"18\"}");
        assertEquals(FilterGroup.Operator.lte, firstFilter(cmd).getOperator());
    }

    @Test
    @DisplayName("gt 操作符")
    void operatorGt() {
        QueryCommand cmd = parseUser("{\"age|gt\":\"18\"}");
        assertEquals(FilterGroup.Operator.gt, firstFilter(cmd).getOperator());
    }

    @Test
    @DisplayName("gte 操作符")
    void operatorGte() {
        QueryCommand cmd = parseUser("{\"age|gte\":\"18\"}");
        assertEquals(FilterGroup.Operator.gte, firstFilter(cmd).getOperator());
    }

    @Test
    @DisplayName("startwith 操作符")
    void operatorStartWith() {
        QueryCommand cmd = parseUser("{\"name|startwith\":\"张\"}");
        assertEquals(FilterGroup.Operator.startWith, firstFilter(cmd).getOperator());
    }

    @Test
    @DisplayName("endwith 操作符")
    void operatorEndWith() {
        QueryCommand cmd = parseUser("{\"name|endwith\":\"三\"}");
        assertEquals(FilterGroup.Operator.endWith, firstFilter(cmd).getOperator());
    }

    @Test
    @DisplayName("contains 操作符")
    void operatorContains() {
        QueryCommand cmd = parseUser("{\"name|contains\":\"三\"}");
        assertEquals(FilterGroup.Operator.contains, firstFilter(cmd).getOperator());
    }

    @Test
    @DisplayName("in 操作符")
    void operatorIn() {
        QueryCommand cmd = parseUser("{\"id|in\":\"1,2,3\"}");
        assertEquals(FilterGroup.Operator.in, firstFilter(cmd).getOperator());
    }

    @Test
    @DisplayName("nin 操作符（JSON 写 nin，枚举名 notin）")
    void operatorNin() {
        QueryCommand cmd = parseUser("{\"id|nin\":\"4,5\"}");
        assertEquals(FilterGroup.Operator.notin, firstFilter(cmd).getOperator());
    }

    @Test
    @DisplayName("nil 操作符（值=1 表示 IS NULL）")
    void operatorNil() {
        QueryCommand cmd = parseUser("{\"email|nil\":\"1\"}");
        FilterGroup.Filter f = firstFilter(cmd);
        assertEquals(FilterGroup.Operator.nil, f.getOperator());
        assertEquals("1", f.getValue());
    }

    @Test
    @DisplayName("nil 操作符（值=0 表示 IS NOT NULL）")
    void operatorNilNot() {
        QueryCommand cmd = parseUser("{\"email|nil\":\"0\"}");
        assertEquals(FilterGroup.Operator.nil, firstFilter(cmd).getOperator());
    }

    @Test
    @DisplayName("bt 操作符（between）")
    void operatorBt() {
        QueryCommand cmd = parseUser("{\"age|bt\":\"[18,60]\"}");
        assertEquals(FilterGroup.Operator.bt, firstFilter(cmd).getOperator());
    }

    @Test
    @DisplayName("fis 操作符（FIND_IN_SET）")
    void operatorFis() {
        QueryCommand cmd = parseUser("{\"name|fis\":\"vip\"}");
        assertEquals(FilterGroup.Operator.fis, firstFilter(cmd).getOperator());
    }

    // ==================== in/nin 值格式变体 ====================

    @Nested
    @DisplayName("in/nin 值格式")
    class InValueFormats {

        @Test
        @DisplayName("in JSON数组格式 [\"1\",\"2\",\"3\"]")
        void inJsonArray() {
            QueryCommand cmd = parseUser("{\"id|in\":[\"1\",\"2\",\"3\"]}");
            Object[] arr = firstFilter(cmd).getValueAsArray();
            assertEquals(3, arr.length);
            assertEquals("1", arr[0]);
            assertEquals("3", arr[2]);
        }

        @Test
        @DisplayName("in 逗号分隔格式 1,2,3")
        void inCommaSeparated() {
            QueryCommand cmd = parseUser("{\"id|in\":\"1,2,3\"}");
            Object[] arr = firstFilter(cmd).getValueAsArray();
            assertEquals(3, arr.length);
        }

        @Test
        @DisplayName("in 单值")
        void inSingleValue() {
            QueryCommand cmd = parseUser("{\"id|in\":\"1\"}");
            Object[] arr = firstFilter(cmd).getValueAsArray();
            assertEquals(1, arr.length);
        }

        @Test
        @DisplayName("nin 逗号分隔")
        void ninCommaSeparated() {
            QueryCommand cmd = parseUser("{\"id|nin\":\"4,5,6\"}");
            Object[] arr = firstFilter(cmd).getValueAsArray();
            assertEquals(3, arr.length);
        }
    }

    // ==================== 多条件组合 ====================

    @Test
    @DisplayName("多条件默认 and 组合")
    void multipleFiltersDefaultAnd() {
        QueryCommand cmd = parseUser("{\"name|eq\":\"张三\",\"age|gt\":\"18\"}");
        List<FilterGroup.Filter> filters = cmd.getWhere().getFilters();
        assertEquals(2, filters.size());
        // 顶层默认 logic=and
        assertEquals(FilterGroup.Logic.and, cmd.getWhere().getLogic());
    }

    // ==================== 非法操作符 ====================

    @Test
    @DisplayName("非法操作符应触发解析失败")
    void invalidOperator() {
        // 不支持的操作符 "xxx"，解析器会 appendMessage，最终校验失败抛异常
        assertThrows(RuntimeException.class, () ->
                parseUser("{\"name|xxx\":\"张三\"}"));
    }
}
