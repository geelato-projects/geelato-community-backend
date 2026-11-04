package cn.geelato.mqltest.support;

import cn.geelato.core.mql.execute.BoundSql;
import org.junit.jupiter.api.Assertions;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * MQL SQL 断言工具。
 * <p>
 * 断言 {@link BoundSql} 的三要素（sql / params / types）与期望值一致。
 * SQL 字符串比对前会做空白规范化（多空格→单空格、trim），避免换行/缩进差异导致脆弱。
 */
public final class MqlSqlAssertions {

    private MqlSqlAssertions() {
    }

    /**
     * 规范化 SQL 字符串：去除首尾空白，将连续空白（含换行）压缩为单个空格。
     */
    public static String normalize(String sql) {
        if (sql == null) {
            return "";
        }
        return sql.trim().replaceAll("\\s+", " ");
    }

    /**
     * 断言生成的 SQL（规范化后）与期望一致。
     */
    public static void assertSqlEquals(String expected, BoundSql actual) {
        assertNotNull(actual, "BoundSql 不应为 null");
        assertNotNull(actual.getSql(), "生成的 SQL 不应为 null");
        assertEquals(normalize(expected), normalize(actual.getSql()),
                "SQL 不匹配");
    }

    /**
     * 断言生成的 SQL（规范化后）与期望一致。
     */
    public static void assertSqlEquals(String expected, String actual) {
        assertEquals(normalize(expected), normalize(actual), "SQL 不匹配");
    }

    /**
     * 断言参数数组。
     */
    public static void assertParams(Object[] expected, BoundSql actual) {
        assertNotNull(actual, "BoundSql 不应为 null");
        assertParams(expected, actual.getParams());
    }

    /**
     * 断言参数数组。
     */
    public static void assertParams(Object[] expected, Object[] actual) {
        Assertions.assertArrayEquals(expected, actual,
                "params 不匹配: expected=" + Arrays.toString(expected) + ", actual=" + Arrays.toString(actual));
    }

    /**
     * 断言参数类型数组（java.sql.Types）。
     */
    public static void assertTypes(int[] expected, BoundSql actual) {
        assertNotNull(actual, "BoundSql 不应为 null");
        assertTypes(expected, actual.getTypes());
    }

    /**
     * 断言参数类型数组（java.sql.Types）。
     */
    public static void assertTypes(int[] expected, int[] actual) {
        Assertions.assertArrayEquals(expected, actual,
                "types 不匹配: expected=" + Arrays.toString(expected) + ", actual=" + Arrays.toString(actual));
    }

    /**
     * 断言 SQL 中包含指定子串（规范化后）。
     */
    public static void assertSqlContains(String expectedSubstring, BoundSql actual) {
        assertNotNull(actual, "BoundSql 不应为 null");
        String normalized = normalize(actual.getSql());
        Assertions.assertTrue(normalized.contains(normalize(expectedSubstring)),
                "SQL 应包含 '" + expectedSubstring + "'，实际为：" + normalized);
    }

    /**
     * 断言 SQL 字符串中包含指定子串（规范化后）。
     */
    public static void assertSqlContains(String expectedSubstring, String actual) {
        String normalized = normalize(actual);
        Assertions.assertTrue(normalized.contains(normalize(expectedSubstring)),
                "SQL 应包含 '" + expectedSubstring + "'，实际为：" + normalized);
    }

    /**
     * 断言 SQL 中不包含指定子串（规范化后）。
     */
    public static void assertSqlNotContains(String unexpectedSubstring, BoundSql actual) {
        assertNotNull(actual, "BoundSql 不应为 null");
        String normalized = normalize(actual.getSql());
        Assertions.assertFalse(normalized.contains(normalize(unexpectedSubstring)),
                "SQL 不应包含 '" + unexpectedSubstring + "'，实际为：" + normalized);
    }
}
