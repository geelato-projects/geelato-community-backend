package cn.geelato.mqltest.sql;

import cn.geelato.core.sql.provider.CustomFunctionRewrites;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * gfn_* 自定义函数方言改写注册表单元测试：
 * 已知内置函数（increment/findinset）按方言与上下文改写；未知函数保留存储函数路径。
 */
@DisplayName("gfn_* 函数方言改写注册表")
class CustomFunctionRewritesTest {

    private static final CustomFunctionRewrites.Context[] ALL_CONTEXTS = {
            CustomFunctionRewrites.Context.CONDITION,
            CustomFunctionRewrites.Context.SELECT,
            CustomFunctionRewrites.Context.SET
    };

    @Test
    @DisplayName("gfn_increment：四方言全上下文改写为 (a + b)")
    void incrementRewritesAllDialectsAllContexts() {
        for (String dbType : new String[]{"mysql", "postgresql", "sqlserver", "oracle"}) {
            for (CustomFunctionRewrites.Context ctx : ALL_CONTEXTS) {
                String result = CustomFunctionRewrites.rewrite(dbType, "gfn_increment(login_count,1)", ctx);
                assertEquals("(login_count + 1)", result,
                        dbType + "/" + ctx + " 应改写为算术表达式");
            }
        }
    }

    @Test
    @DisplayName("gfn_findinset：仅 CONDITION 上下文改写")
    void findinsetRewritesOnlyConditionContext() {
        assertEquals("position((',' || 'vip' || ',') in (',' || tags || ','))",
                CustomFunctionRewrites.rewrite("postgresql", "gfn_findinset('vip',tags)",
                        CustomFunctionRewrites.Context.CONDITION));
        assertEquals("CHARINDEX(CONCAT(',', 'vip', ','), CONCAT(',', tags, ','))",
                CustomFunctionRewrites.rewrite("sqlserver", "gfn_findinset('vip',tags)",
                        CustomFunctionRewrites.Context.CONDITION));
        assertEquals("INSTR(',' || tags || ',', ',' || 'vip' || ',')",
                CustomFunctionRewrites.rewrite("oracle", "gfn_findinset('vip',tags)",
                        CustomFunctionRewrites.Context.CONDITION));
        // mysql 保留 geelato.gfn_findinset 存储函数路径（存量行为零变化）
        assertNull(CustomFunctionRewrites.rewrite("mysql", "gfn_findinset('vip',tags)",
                CustomFunctionRewrites.Context.CONDITION));
        // SELECT/SET 上下文不改写
        assertNull(CustomFunctionRewrites.rewrite("postgresql", "gfn_findinset('vip',tags)",
                CustomFunctionRewrites.Context.SELECT));
        assertNull(CustomFunctionRewrites.rewrite("postgresql", "gfn_findinset('vip',tags)",
                CustomFunctionRewrites.Context.SET));
    }

    @Test
    @DisplayName("未注册函数 / 参数含引号逗号解析：不改写或正确切分")
    void unknownFunctionAndQuotedParams() {
        assertNull(CustomFunctionRewrites.rewrite("postgresql", "gfn_unknown(a,b)",
                CustomFunctionRewrites.Context.CONDITION));
        assertNull(CustomFunctionRewrites.rewrite("postgresql", "gfn_increment(a,b)",
                null));
        // 单引号字符串内的逗号不切分
        String result = CustomFunctionRewrites.rewrite("postgresql", "gfn_increment('a,b',1)",
                CustomFunctionRewrites.Context.SET);
        assertEquals("('a,b' + 1)", result);
    }

    @Test
    @DisplayName("非 gfn_ 形态输入返回 null")
    void malformedExpressionReturnsNull() {
        assertNull(CustomFunctionRewrites.rewrite("postgresql", "no_parens", CustomFunctionRewrites.Context.CONDITION));
        assertNull(CustomFunctionRewrites.rewrite(null, "gfn_increment(a,1)", CustomFunctionRewrites.Context.CONDITION));
    }
}
