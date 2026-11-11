package cn.geelato.web.platform.srv.mv;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 源库视图展开器：把建表语句中引用的 MySQL 视图递归内联展开为底层物理表。
 * <p>mysql-cdc 只能摄取物理表（binlog 行变更），视图必须下钻。展开结果：
 * 语句中的视图引用替换为 "(视图定义)" 子查询（别名与语义保持不变），并给出全部物理表清单。
 * <p>替换复用 {@link MvSqlRewriter}（大小写不敏感、跳过字符串/注释、别名.列不误伤），
 * 展开逐层迭代直到语句中不再有视图引用；视图链循环引用与过深嵌套硬失败。
 */
@Slf4j
public class MvViewExpander {

    private static final int MAX_DEPTH = 10;
    private static final Set<String> SQL_TABLE_KEYWORDS = Set.of(
            "select", "where", "on", "left", "right", "inner", "outer", "join",
            "group", "order", "limit", "offset", "union", "as", "and", "or", "not", "by", "having", "from", "distinct");

    public record Expansion(String expandedSql, List<String> physicalTables, List<String> expandedViews) {
    }

    public Expansion expand(Connection conn, String database, String sql, List<String> extraTables) throws SQLException {
        String current = sql;
        List<String> expandedViews = new ArrayList<>();
        Set<String> visiting = new LinkedHashSet<>();
        List<String> references = extractReferences(current);
        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            Map<String, String> viewMappings = new LinkedHashMap<>();
            Map<String, String> types = queryTableTypes(conn, database, references);
            for (String ref : references) {
                String type = types.get(ref.toLowerCase(Locale.ROOT));
                if (type == null) {
                    throw new IllegalStateException(String.format("源库 %s 中不存在表或视图：%s（建表语句/源表清单引用了不存在的对象）", database, ref));
                }
                if ("VIEW".equalsIgnoreCase(type)) {
                    if (!visiting.add(ref.toLowerCase(Locale.ROOT))) {
                        throw new IllegalStateException("视图展开遇到循环引用：" + ref);
                    }
                    expandedViews.add(ref);
                    String definition = showCreateView(conn, database, ref);
                    log.info("[MV-Expand] 展开视图：{}.{}（定义 {} 字符）", database, ref, definition.length());
                    viewMappings.put(ref, "(" + definition + ")");
                }
            }
            if (viewMappings.isEmpty()) {
                break;
            }
            current = MvSqlRewriter.rewrite(current, viewMappings);
            references = extractReferences(current);
            if (depth == MAX_DEPTH - 1 && containsView(conn, database, references)) {
                throw new IllegalStateException("视图嵌套超过 " + MAX_DEPTH + " 层，拒绝继续展开");
            }
        }
        // 物理表清单：按小写去重（SQL 内写法与前端源表清单大小写可能不同），保留原始拼写
        Map<String, String> physical = new LinkedHashMap<>();
        for (String ref : references) {
            physical.putIfAbsent(ref.toLowerCase(Locale.ROOT), ref);
        }
        for (String extra : extraTables) {
            collectPhysical(conn, database, extra.trim(), physical, visiting);
        }
        return new Expansion(current, new ArrayList<>(physical.values()), expandedViews);
    }

    private boolean containsView(Connection conn, String database, List<String> references) throws SQLException {
        Map<String, String> types = queryTableTypes(conn, database, references);
        return references.stream().anyMatch(r -> "VIEW".equalsIgnoreCase(types.get(r.toLowerCase(Locale.ROOT))));
    }

    private void collectPhysical(Connection conn, String database, String name, Map<String, String> out, Set<String> visiting) throws SQLException {
        String lower = name.toLowerCase(Locale.ROOT);
        Map<String, String> types = queryTableTypes(conn, database, List.of(name));
        String type = types.get(lower);
        if (type == null) {
            throw new IllegalStateException("源库 " + database + " 中不存在表或视图：" + name);
        }
        if ("BASE TABLE".equalsIgnoreCase(type)) {
            out.putIfAbsent(lower, name);
            return;
        }
        if (!visiting.add(lower)) {
            throw new IllegalStateException("视图展开遇到循环引用：" + name);
        }
        for (String ref : extractReferences(showCreateView(conn, database, name))) {
            collectPhysical(conn, database, ref, out, visiting);
        }
    }

    private String showCreateView(Connection conn, String database, String viewName) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SHOW CREATE VIEW `" + database + "`.`" + viewName + "`")) {
            if (!rs.next()) {
                throw new IllegalStateException("读取视图定义失败：" + database + "." + viewName);
            }
            return normalizeMysqlSql(rs.getString(1));
        }
    }

    static String normalizeMysqlSql(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }
        StringBuilder out = new StringBuilder(sql.length());
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '\'' || c == '"') {
                int end = skipQuoted(sql, i, c);
                out.append(sql, i, end);
                i = end;
                continue;
            }
            if (c == '`') {
                StringBuilder identifier = new StringBuilder();
                int j = i + 1;
                while (j < n) {
                    if (sql.charAt(j) == '`') {
                        if (j + 1 < n && sql.charAt(j + 1) == '`') {
                            identifier.append('`');
                            j += 2;
                            continue;
                        }
                        break;
                    }
                    identifier.append(sql.charAt(j));
                    j++;
                }
                out.append(MvSqlRewriter.plainIdentifier(identifier.toString()));
                i = j + 1;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private Map<String, String> queryTableTypes(Connection conn, String database, List<String> names) throws SQLException {
        Map<String, String> result = new LinkedHashMap<>();
        if (names == null || names.isEmpty()) {
            return result;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(names.size(), "?"));
        String sql = "SELECT lower(table_name), table_type FROM information_schema.tables WHERE table_schema = ? AND lower(table_name) IN (" + placeholders + ")";
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, database);
            int index = 2;
            for (String name : names) {
                ps.setString(index++, name.toLowerCase(Locale.ROOT));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString(1), rs.getString(2));
                }
            }
        }
        return result;
    }

    static List<String> extractReferences(String sql) {
        List<String> names = new ArrayList<>();
        if (sql == null || sql.isEmpty()) {
            return names;
        }
        int i = 0;
        int n = sql.length();
        String lastKeyword = "";
        StringBuilder token = new StringBuilder();
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                int end = sql.indexOf('\n', i);
                i = end < 0 ? n : end;
                continue;
            }
            if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                i = skipBlockComment(sql, i);
                continue;
            }
            if (c == '`' && ("from".equals(lastKeyword) || "join".equals(lastKeyword))) {
                // 反引号标识符：`tbl` / `db`.`tbl`，取表名部分
                int end = skipQuoted(sql, i, '`');
                String identifier = sql.substring(i + 1, Math.max(i + 1, end - 1)).replace("``", "`");
                if (!identifier.isEmpty() && !identifier.matches("\\d+")) {
                    if (!nextNonWhitespaceIs(sql, end, '.')) {
                        names.add(identifier);
                        lastKeyword = "";
                    }
                    // 库限定（`db`.`tbl`）时保留 lastKeyword，等待后续表名
                }
                i = end;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                i = skipQuoted(sql, i, c);
                continue;
            }
            if (Character.isLetterOrDigit(c) || c == '_' || c == '$') {
                token.setLength(0);
                while (i < n && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_' || sql.charAt(i) == '$')) {
                    token.append(sql.charAt(i));
                    i++;
                }
                String word = token.toString();
                String lower = word.toLowerCase(Locale.ROOT);
                if (SQL_TABLE_KEYWORDS.contains(lower)) {
                    lastKeyword = lower;
                } else if (("from".equals(lastKeyword) || "join".equals(lastKeyword))
                        && !word.matches("\\d+")) {
                    if (!nextNonWhitespaceIs(sql, i, '.')) {
                        names.add(word);
                        lastKeyword = "";
                    }
                } else {
                    lastKeyword = "";
                }
                continue;
            }
            if (c == '(') {
                lastKeyword = "";
            } else if (!Character.isWhitespace(c) && c != '.') {
                lastKeyword = "";
            }
            i++;
        }
        return names.stream().distinct().toList();
    }

    private static int skipBlockComment(String sql, int start) {
        int i = start + 2;
        while (i + 1 < sql.length()) {
            if (sql.charAt(i) == '*' && sql.charAt(i + 1) == '/') {
                return i + 2;
            }
            i++;
        }
        return sql.length();
    }

    private static boolean nextNonWhitespaceIs(String sql, int from, char expected) {
        for (int i = from; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            return c == expected;
        }
        return false;
    }

    private static int skipQuoted(String sql, int start, char quote) {
        int i = start + 1;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '\\' && quote != '`') {
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
}
