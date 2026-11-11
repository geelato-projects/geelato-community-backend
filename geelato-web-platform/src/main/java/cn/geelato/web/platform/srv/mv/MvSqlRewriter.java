package cn.geelato.web.platform.srv.mv;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * SQL 源表名词法改写器（引擎中立）。
 * <p>把物化视图定义 SQL 中以源库表名书写的表引用，替换为引擎内的表名（映射来自
 * {@code MaterializedViewEngine.buildDdlPlan} 的内省核实结果）。
 * <p>单趟扫描，跳过字符串字面量（' 与 "，含反斜杠转义与成对引号转义）、
 * 行注释与块注释；MySQL 反引号标识符按去引号后的名字参与匹配，命中则整体替换。
 * 匹配大小写不敏感；紧跟 "(" 的标识符视为函数名不做替换。
 * <p>点号限定的歧义处理（X.T 且 T 命中表名白名单）：
 * T 后紧跟 "." 视为表限定列引用（table.column），替换 T；
 * T 前是 "." 且前一标识符是 schema 限定符（源库名）视为库限定表引用（db.table），替换 T；
 * 其余（别名.列，alias.column）不替换，避免别名列与表名同名时误改。
 */
public final class MvSqlRewriter {

    private MvSqlRewriter() {
    }

    public static String rewrite(String sql, Map<String, String> tableMapping) {
        return rewrite(sql, tableMapping, Set.of());
    }

    public static String rewrite(String sql, Map<String, String> tableMapping, Set<String> schemaQualifiers) {
        if (sql == null || sql.isEmpty() || tableMapping == null || tableMapping.isEmpty()) {
            return sql;
        }
        Map<String, String> mapping = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        mapping.putAll(tableMapping);
        Set<String> qualifiers = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        qualifiers.addAll(schemaQualifiers == null ? Set.of() : schemaQualifiers);
        StringBuilder out = new StringBuilder(sql.length() + 64);
        String prevIdentifier = null;
        char prevSignificant = 0;
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                int end = sql.indexOf('\n', i);
                end = end < 0 ? n : end;
                out.append(sql, i, end);
                i = end;
                continue;
            }
            if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int end = findBlockCommentEnd(sql, i);
                out.append(sql, i, end);
                i = end;
                continue;
            }
            if (c == '\'' || c == '"') {
                int end = scanQuoted(sql, i, c, true);
                out.append(sql, i, end);
                i = end;
                prevIdentifier = null;
                prevSignificant = '\'';
                continue;
            }
            if (c == '`') {
                int end = scanQuoted(sql, i, '`', false);
                String identifier = sql.substring(i + 1, end - 1).replace("``", "`");
                String mapped = mapping.get(identifier);
                if (mapped != null && shouldReplace(sql, end, prevSignificant, prevIdentifier, qualifiers)) {
                    out.append(mapped);
                } else {
                    out.append(plainIdentifier(identifier));
                }
                i = end;
                prevIdentifier = identifier;
                prevSignificant = '`';
                continue;
            }
            if (isIdentifierStart(c)) {
                int end = i + 1;
                while (end < n && isIdentifierPart(sql.charAt(end))) {
                    end++;
                }
                String token = sql.substring(i, end);
                String mapped = mapping.get(token);
                if (mapped != null && shouldReplace(sql, end, prevSignificant, prevIdentifier, qualifiers)) {
                    out.append(mapped);
                    prevIdentifier = mapped;
                } else {
                    out.append(token);
                    prevIdentifier = token;
                }
                i = end;
                prevSignificant = sql.charAt(end - 1);
                continue;
            }
            out.append(c);
            if (!Character.isWhitespace(c)) {
                prevSignificant = c;
                if (c != '.') {
                    prevIdentifier = null;
                }
            }
            i++;
        }
        return out.toString();
    }

    private static boolean shouldReplace(String sql, int tokenEnd,
                                         char prevSignificant, String prevIdentifier,
                                         Set<String> qualifiers) {
        if (nextSignificantIs(sql, tokenEnd, '(')) {
            return false;
        }
        if (nextSignificantIs(sql, tokenEnd, '.')) {
            return true;
        }
        if (prevSignificant == '.') {
            return prevIdentifier != null && qualifiers.contains(prevIdentifier);
        }
        return true;
    }

    private static boolean nextSignificantIs(String sql, int from, char expected) {
        for (int i = from; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            return c == expected;
        }
        return false;
    }

    static String plainIdentifier(String identifier) {
        String lower = identifier.toLowerCase(Locale.ROOT);
        return lower.matches("[a-z0-9_]+") ? lower : "\"" + lower + "\"";
    }

    private static int findBlockCommentEnd(String sql, int start) {
        int i = start + 2;
        while (i + 1 < sql.length()) {
            if (sql.charAt(i) == '*' && sql.charAt(i + 1) == '/') {
                return i + 2;
            }
            i++;
        }
        return sql.length();
    }

    private static int scanQuoted(String sql, int start, char quote, boolean escapeByBackslash) {
        int i = start + 1;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (escapeByBackslash && c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote) {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return sql.length();
    }

    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }
}
