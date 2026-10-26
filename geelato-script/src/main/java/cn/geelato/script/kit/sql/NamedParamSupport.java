package cn.geelato.script.kit.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * :name 命名参数的掩码/还原：改写前把 :x 掩成 ?（Calcite 解析器不识别命名参数），
 * 改写后按出现顺序还原。扫描感知字符串/三种引用标识符/两种注释/PG :: 转换；
 * 含裸位置参数 ? 的语句不掩码（Optional.empty），由调用方原样透传。
 * INTERVAL 位置参数（INTERVAL :x unit）掩成 INTERVAL (?) unit：Calcite 区间字面量语法
 * 不接受裸动态参数（掩成 ? 必然解析失败，整句透传，跨库改写随之失效），括号形式可解析、
 * Calcite 反解析保形、联邦路径按 ? 出现顺序绑定仍成立。还原按掩码记录去括号回填 :x；
 * 用户自带括号的普通参数（如 IN (:id)）同样掩成 (?)，靠记录的 interval 标记区分——
 * 普通参数保留括号、INTERVAL 参数去掉掩码括号，掩码文本中的 (?) 因此不存在歧义
 * （原文含裸 ? 时整体不掩码，(?) 不可能来自原文）。
 */
public final class NamedParamSupport {

    /** 单个命名参数：名字 + 是否处于 INTERVAL 位置（决定掩码形态 (?) 与裸 ? 之别）。 */
    public record Param(String name, boolean interval) {
    }

    public record Masked(String sql, List<Param> params) {

        /** 按出现顺序的参数名（联邦路径按 ? 出现顺序绑定用）。 */
        public List<String> order() {
            List<String> names = new ArrayList<>(params.size());
            for (Param p : params) {
                names.add(p.name());
            }
            return names;
        }
    }

    public static Optional<Masked> mask(String sql) {
        if (sql == null || sql.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder out = new StringBuilder(sql.length());
        List<Param> params = new ArrayList<>();
        // 词法跟踪：最近完成的词是否为 INTERVAL 关键字（大小写不敏感），决定 :x 的掩码形态
        StringBuilder word = null;
        boolean lastWordInterval = false;
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            char next = i + 1 < n ? sql.charAt(i + 1) : '\0';
            if (c == '\'' || c == '"' || c == '`') {
                word = null;
                int end = consumeQuoted(sql, i, c);
                out.append(sql, i, end);
                i = end;
                continue;
            }
            if (c == '-' && next == '-') {
                word = null;
                int end = sql.indexOf('\n', i);
                end = end < 0 ? n : end;
                out.append(sql, i, end);
                i = end;
                continue;
            }
            if (c == '/' && next == '*') {
                word = null;
                int end = sql.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                out.append(sql, i, end);
                i = end;
                continue;
            }
            if (c == '?') {
                return Optional.empty();
            }
            if (c == ':') {
                if (next == ':') {
                    word = null;
                    lastWordInterval = false;
                    out.append("::");
                    i += 2;
                    continue;
                }
                if (Character.isJavaIdentifierStart(next)) {
                    int j = i + 1;
                    while (j < n && Character.isJavaIdentifierPart(sql.charAt(j))) {
                        j++;
                    }
                    String name = sql.substring(i + 1, j);
                    boolean interval = lastWordInterval;
                    params.add(new Param(name, interval));
                    out.append(interval ? "(?)" : "?");
                    i = j;
                    word = null;
                    lastWordInterval = false;
                    continue;
                }
            }
            if (Character.isJavaIdentifierPart(c)) {
                if (word == null) {
                    word = new StringBuilder();
                }
                word.append(c);
                out.append(c);
                i++;
                continue;
            }
            if (word != null) {
                lastWordInterval = word.toString().equalsIgnoreCase("INTERVAL");
                word = null;
            }
            out.append(c);
            i++;
        }
        return Optional.of(new Masked(out.toString(), params));
    }

    /**
     * 改写后的 SQL 按出现顺序还原 :name：普通参数 ? → :name（用户自带括号原样保留），
     * INTERVAL 参数 (?) → :name（去掉掩码括号，还原成原文形态）；数量/形态不匹配抛
     * IllegalStateException（内部一致性护栏）。
     */
    public static String restore(String sql, List<Param> params) {
        if (params == null || params.isEmpty()) {
            return sql;
        }
        StringBuilder out = new StringBuilder(sql.length() + 16);
        int i = 0;
        int n = sql.length();
        int idx = 0;
        while (i < n) {
            char c = sql.charAt(i);
            char next = i + 1 < n ? sql.charAt(i + 1) : '\0';
            if (c == '\'' || c == '"' || c == '`') {
                int end = consumeQuoted(sql, i, c);
                out.append(sql, i, end);
                i = end;
                continue;
            }
            if (c == '-' && next == '-') {
                int end = sql.indexOf('\n', i);
                end = end < 0 ? n : end;
                out.append(sql, i, end);
                i = end;
                continue;
            }
            if (c == '/' && next == '*') {
                int end = sql.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                out.append(sql, i, end);
                i = end;
                continue;
            }
            boolean parenParam = c == '(' && next == '?' && i + 2 < n && sql.charAt(i + 2) == ')';
            if (parenParam || c == '?') {
                if (idx >= params.size()) {
                    throw new IllegalStateException("命名参数还原越界: ? 数量超过掩码记录 " + params.size());
                }
                Param p = params.get(idx);
                if (parenParam) {
                    // 普通参数（用户自带括号，如 IN (:id)）保留括号；INTERVAL 参数去掉掩码括号
                    out.append(p.interval() ? ":" : "(:").append(p.name());
                    if (!p.interval()) {
                        out.append(')');
                    }
                } else {
                    if (p.interval()) {
                        throw new IllegalStateException("命名参数还原形态不匹配: INTERVAL 参数 :" + p.name()
                                + " 缺少 (?) 掩码");
                    }
                    out.append(':').append(p.name());
                }
                idx++;
                i += parenParam ? 3 : 1;
                continue;
            }
            out.append(c);
            i++;
        }
        if (idx != params.size()) {
            throw new IllegalStateException("命名参数还原不完整: ? 数量 " + idx + " < 掩码记录 " + params.size());
        }
        return out.toString();
    }

    /** 按掩码顺序取参数值；缺名直接硬失败。 */
    public static Object[] bindOrder(Map<String, Object> params, List<String> order) {
        Object[] args = new Object[order.size()];
        for (int i = 0; i < order.size(); i++) {
            String name = order.get(i);
            if (params == null || !params.containsKey(name)) {
                throw new IllegalArgumentException("命名参数缺失: :" + name);
            }
            args[i] = params.get(name);
        }
        return args;
    }

    /** 消费到闭引号（含成对转义 ''/""/``）后的下一位置。 */
    private static int consumeQuoted(String sql, int start, char quote) {
        int i = start + 1;
        int n = sql.length();
        while (i < n) {
            if (sql.charAt(i) == quote) {
                if (i + 1 < n && sql.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return n;
    }
}
