package cn.geelato.core.meta.model.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FuzzymatchSupport 等价改写单元测试：清洗/转义规则与数据库函数
 * geelato.gfn_fuzzymatch 函数体逐步对齐（含全部已识别的边界行为）。
 */
class FuzzymatchSupportTest {

    @Test
    void identifyFuzzymatchExpressions() {
        assertTrue(FuzzymatchSupport.isFuzzymatch("gfn_fuzzymatch(so_no,'123')"));
        assertTrue(FuzzymatchSupport.isFuzzymatch("fuzzymatch($e.soNo,'123')"));
        assertFalse(FuzzymatchSupport.isFuzzymatch("gfn_increment(x)"));
        assertFalse(FuzzymatchSupport.isFuzzymatch("so_no"));
        assertFalse(FuzzymatchSupport.isFuzzymatch(null));
    }

    @Test
    void parseParamsExtractsColumnAndKeyword() {
        String[] ps = FuzzymatchSupport.parseParams("gfn_fuzzymatch(so_no,'274935136')");
        assertEquals("so_no", ps[0]);
        assertEquals("274935136", ps[1]);

        // 关键字内空格保留（GFunction 生成时仅做外层 trim）
        ps = FuzzymatchSupport.parseParams("gfn_fuzzymatch(title,'ab c')");
        assertEquals("ab c", ps[1]);

        // 非法形态返回 null（走原函数路径）
        assertNull(FuzzymatchSupport.parseParams("gfn_fuzzymatch(col)"));
        assertNull(FuzzymatchSupport.parseParams("gfn_fuzzymatch(col,bare)"));
        assertNull(FuzzymatchSupport.parseParams("so_no"));
    }

    @Test
    void buildPatternPlainKeyword() {
        assertEquals("274935136", FuzzymatchSupport.buildRegexPattern("274935136"));
    }

    @Test
    void buildPatternEscapesBackslashThenDot() {
        // 函数体顺序：先 \ 后 .（逐步复刻，包含连续转义的叠加效果）
        assertEquals("a\\.b", FuzzymatchSupport.buildRegexPattern("a.b"));
        assertEquals("a\\\\b", FuzzymatchSupport.buildRegexPattern("a\\b"));
        assertEquals("a\\\\\\.b", FuzzymatchSupport.buildRegexPattern("a\\.b"));
    }

    @Test
    void buildPatternMultiTermOr() {
        assertEquals("SO111|SO222", FuzzymatchSupport.buildRegexPattern("SO111,SO222"));
        // 全角逗号 → 英文后拆分
        assertEquals("SO111|SO222", FuzzymatchSupport.buildRegexPattern("SO111，SO222"));
    }

    @Test
    void buildPatternSinglePassCommaMergeKeepsNonIdempotentBehavior() {
        // 函数体 REPLACE(',,',',') 单遍非重叠：两个逗号合并为一个；三个逗号清洗后仍剩两个，
        // pattern 的单逗号→| 产生空正则分支（'a||b' 空分支匹配任意非空串——原函数行为，如实复刻）
        assertEquals("a|b", FuzzymatchSupport.buildRegexPattern("a,,b"));
        assertEquals("a||b", FuzzymatchSupport.buildRegexPattern("a,,,b"));
    }

    @Test
    void buildPatternTrimSpacesOnly() {
        assertEquals("abc", FuzzymatchSupport.buildRegexPattern("  abc  "));
        // TRIM 仅空格：制表符是字面量（与 Java String#trim 不同）
        assertEquals("\tabc", FuzzymatchSupport.buildRegexPattern("\tabc"));
    }

    @Test
    void buildPatternEmptyAfterCleanReturnsNeverMatch() {
        // 清洗后为空的仅纯空格/空串
        assertEquals(FuzzymatchSupport.NEVER_MATCH_REGEX, FuzzymatchSupport.buildRegexPattern(""));
        assertEquals(FuzzymatchSupport.NEVER_MATCH_REGEX, FuzzymatchSupport.buildRegexPattern("   "));
        assertEquals(FuzzymatchSupport.NEVER_MATCH_REGEX, FuzzymatchSupport.buildRegexPattern(null));
        // 纯逗号清洗后仍非空（',,'→','→pattern '|'，空正则分支恒真——原函数行为，如实复刻）
        assertEquals("|", FuzzymatchSupport.buildRegexPattern(",,"));
        assertEquals("||", FuzzymatchSupport.buildRegexPattern(",,,"));
    }

    @Test
    void neverMatchRegexMatchesNothing() {
        // a^ = 字面 a 之后要求行首，恒不成立（HS/ICU 均兼容，无扩展语法）
        assertEquals("a^", FuzzymatchSupport.NEVER_MATCH_REGEX);
    }

    @Test
    void containsRegexMetaDetectsMetacharacters() {
        assertFalse(FuzzymatchSupport.containsRegexMeta("274935136"));
        assertFalse(FuzzymatchSupport.containsRegexMeta("SO-123"));
        // \ 与 . 已被原函数转义为字面量，不构成正则语义差异
        assertFalse(FuzzymatchSupport.containsRegexMeta("a.b"));
        assertFalse(FuzzymatchSupport.containsRegexMeta("a\\b"));
        assertTrue(FuzzymatchSupport.containsRegexMeta("a+b"));
        assertTrue(FuzzymatchSupport.containsRegexMeta("a*b"));
        assertTrue(FuzzymatchSupport.containsRegexMeta("a|b"));
        assertTrue(FuzzymatchSupport.containsRegexMeta("a(b"));
        assertTrue(FuzzymatchSupport.containsRegexMeta("a[b"));
        assertFalse(FuzzymatchSupport.containsRegexMeta(null));
    }

    @Test
    void splitTermsFollowsFunctionCleaning() {
        // 不逐词 trim：词内空格是字面量（整体 TRIM 后按逗号原样切分）
        assertArrayEquals(new String[]{"SO111", "SO222"},
                FuzzymatchSupport.splitTerms("SO111,SO222").toArray());
        assertArrayEquals(new String[]{"SO111 ", " SO222"},
                FuzzymatchSupport.splitTerms(" SO111 ， SO222 ").toArray());
        // 三逗号清洗后残留双逗号：切分出空段（对应函数 pattern 的空正则分支，
        // 路由侧靠"逐词 pattern 重拼 == 整体 pattern"一致性校验拦截此类关键字）
        assertArrayEquals(new String[]{"a", "", "b"},
                FuzzymatchSupport.splitTerms("a,,,b").toArray());
        assertArrayEquals(new String[]{"", ""},
                FuzzymatchSupport.splitTerms(",,").toArray());
        assertArrayEquals(new String[]{"", "", ""},
                FuzzymatchSupport.splitTerms(",,,").toArray());
        assertTrue(FuzzymatchSupport.splitTerms(null).isEmpty());
        assertTrue(FuzzymatchSupport.splitTerms("  ").isEmpty());
    }

    @Test
    void perTermPatternRebuildMatchesWholePatternForPlainMultiTerm() {
        // 一致性校验的通过样例：普通多词逐词 pattern 以 | 重拼 == 整体 pattern
        String keyword = "SO111,SO222";
        String whole = FuzzymatchSupport.buildRegexPattern(keyword);
        List<String> terms = FuzzymatchSupport.splitTerms(keyword);
        StringBuilder rebuilt = new StringBuilder();
        for (int i = 0; i < terms.size(); i++) {
            if (i > 0) {
                rebuilt.append('|');
            }
            rebuilt.append(FuzzymatchSupport.buildRegexPattern(terms.get(i)));
        }
        assertEquals(whole, rebuilt.toString());
    }
}
