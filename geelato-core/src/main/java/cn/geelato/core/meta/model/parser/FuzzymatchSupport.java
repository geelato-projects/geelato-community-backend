package cn.geelato.core.meta.model.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * fuzzymatch 函数的解析与等价改写支撑。
 *
 * <p>等价改写的唯一依据是数据库函数 {@code geelato.gfn_fuzzymatch(field_val, search_str)} 的函数体，
 * 其语义为：清洗 search_str（全角逗号转英文、TRIM 首尾空格、单遍合并连续两个逗号），
 * 清洗后为空返回 0；否则只转义 {@code \} 和 {@code .}，逗号转 {@code |}，
 * 执行 {@code field_val REGEXP pattern}，命中返回 1，不命中返回 0；
 * field_val 或 search_str 为 NULL/空串时直接返回 0。
 *
 * <p>因此 {@code gfn_fuzzymatch(col,'kw') > 0} 严格等价于
 * {@code (col <> '' AND col REGEXP ?)}，其中 ? 绑定 {@link #buildRegexPattern(String)} 的结果——
 * {@code col <> ''} 复刻函数对空串 field_val 的短路返回（REGEXP 对空串的行为与函数不同，
 * 例如未转义的 {@code a*} 能匹配空串），NULL 在两侧均表现为不命中。
 * 清洗结果为空时函数恒返回 0，绑定 {@link #NEVER_MATCH_REGEX}（永不匹配的正则）保持恒假。
 *
 * <p>本类同时供搜索路由判定使用：{@link #containsRegexMeta(String)} 检测关键字是否含
 * 正则元字符——原函数将关键字按正则求值（只转义 {@code \} 和 {@code .}），
 * 字面量检索引擎（如 Lucene）无法等价复刻该行为，含元字符时必须回退 REGEXP 改写。
 */
public final class FuzzymatchSupport {

    public static final String FUNC_PREFIX = "gfn_fuzzymatch(";
    public static final String FUNC_PREFIX_UNRESOLVED = "fuzzymatch(";

    /**
     * 永不匹配的正则：字面 a 之后要求行首，恒不成立。
     * MySQL 5.7（Henry Spencer）与 8.0（ICU）均兼容，无扩展语法。
     */
    public static final String NEVER_MATCH_REGEX = "a^";

    /** 原函数未转义、会被 REGEXP 按正则求值的元字符（\ 与 . 已由函数转义，不计入）。 */
    private static final String REGEX_META_CHARS = "*+?()[]{}|^$";

    private FuzzymatchSupport() {
    }

    /** field 是否为（已解析或未解析的）fuzzymatch 函数表达式。 */
    public static boolean isFuzzymatch(String field) {
        return field != null && (field.startsWith(FUNC_PREFIX) || field.startsWith(FUNC_PREFIX_UNRESOLVED));
    }

    /**
     * 解析函数表达式参数。
     *
     * @param field 形如 {@code gfn_fuzzymatch(col_name,'keyword')}
     * @return [0]=列名表达式（不含别名装饰），[1]=关键字（已去引号）；无法确定性解析时返回 null
     */
    public static String[] parseParams(String field) {
        if (field == null) {
            return null;
        }
        int open = field.indexOf('(');
        int close = field.lastIndexOf(')');
        if (open < 0 || close <= open) {
            return null;
        }
        String inner = field.substring(open + 1, close);
        String[] ps = splitParamsRespectQuotes(inner);
        if (ps.length != 2) {
            return null;
        }
        String col = ps[0].trim();
        String keyword = ps[1].trim();
        if (col.isEmpty() || keyword.length() < 2
                || keyword.charAt(0) != '\'' || keyword.charAt(keyword.length() - 1) != '\'') {
            return null;
        }
        keyword = keyword.substring(1, keyword.length() - 1);
        return new String[]{col, keyword};
    }

    /**
     * 解析函数第一参数为列名表达式（见 {@link #resolveColumn(String, String)}，无当前实体上下文）。
     */
    public static String resolveColumn(String param) {
        return resolveColumn(param, null);
    }

    /**
     * 解析函数第一参数为列名表达式：{@code $self.field} 按当前实体解析——
     * 括号组（@b or）内的函数条件不经 getMysqlFunction 归一，{@code $self} 原样保留；
     * {@code $entity.field} 经元数据解析；普通列名原样返回；无法解析返回 null。
     */
    public static String resolveColumn(String param, String selfEntityName) {
        if (param == null || param.isEmpty()) {
            return null;
        }
        if (!param.startsWith("$")) {
            return param;
        }
        String[] parts = param.split("\\.");
        if (parts.length != 2) {
            return null;
        }
        String refName = parts[0].substring(1);
        String entity = "self".equals(refName) ? selfEntityName : refName;
        if (entity == null || entity.isBlank()) {
            return null;
        }
        try {
            cn.geelato.core.meta.model.entity.EntityMeta em =
                    cn.geelato.core.meta.MetaManager.singleInstance().getByEntityName(entity);
            return em != null ? em.getColumnName(parts[1]) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 复刻函数体的 search_str 清洗与正则构建（步骤与顺序严格对齐函数体）：
     * 全角逗号→英文；单遍 REPLACE(',,',',')（非重叠，三个逗号变两个的行为保留）；
     * TRIM 仅去首尾空格；为空返回 {@link #NEVER_MATCH_REGEX}；转义 \ 与 .（先 \ 后 .）；逗号→|。
     */
    public static String buildRegexPattern(String keyword) {
        if (keyword == null) {
            return NEVER_MATCH_REGEX;
        }
        String s = keyword.replace('，', ',');
        s = s.replace(",,", ",");
        s = trimSpacesOnly(s);
        if (s.isEmpty()) {
            return NEVER_MATCH_REGEX;
        }
        s = s.replace("\\", "\\\\").replace(".", "\\.");
        return s.replace(",", "|");
    }

    /**
     * 按原函数清洗规则拆分关键词（全角逗号→英文、单遍合并连续两个逗号、TRIM 仅空格后按逗号切分）。
     * <b>不做逐词 trim、不丢弃空段</b>——与函数行为严格一致（词内空格是字面量；
     * 连续逗号经清洗仍可能残留空段，对应函数 pattern 的空正则分支）。拆分结果与
     * {@link #buildRegexPattern} 可能语义不等（空段/词内空格），路由侧必须做一致性校验
     * （逐词 pattern 以 | 重拼后与整体 pattern 相等才可路由）。
     */
    public static List<String> splitTerms(String keyword) {
        if (keyword == null) {
            return List.of();
        }
        String s = keyword.replace('，', ',');
        s = s.replace(",,", ",");
        s = trimSpacesOnly(s);
        if (s.isEmpty()) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        for (String part : s.split(",", -1)) {
            terms.add(part);
        }
        return terms;
    }

    /**
     * 关键字（原始、未清洗）是否含正则元字符。
     * 含元字符时原函数按正则求值（如输入 {@code a+} 可命中 {@code aab}），
     * 字面量检索引擎不等价，必须回退 REGEXP 改写（REGEXP 改写保留原样，语义一致）。
     */
    public static boolean containsRegexMeta(String keyword) {
        if (keyword == null) {
            return false;
        }
        for (int i = 0; i < keyword.length(); i++) {
            char c = keyword.charAt(i);
            if (REGEX_META_CHARS.indexOf(c) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按逗号切分（单引号内不切分），与 GFunction#splitParamsRespectQuotes 逻辑一致。
     */
    static String[] splitParamsRespectQuotes(String s) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'') {
                inSingle = !inSingle;
                cur.append(c);
                continue;
            }
            if (c == ',' && !inSingle) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (!cur.isEmpty()) {
            parts.add(cur.toString());
        }
        return parts.toArray(new String[0]);
    }

    /** MySQL TRIM(str) 语义：仅移除首尾空格（不同于 Java String#trim 会移除 ≤ U+0020 的控制字符）。 */
    static String trimSpacesOnly(String s) {
        int st = 0;
        int en = s.length();
        while (st < en && s.charAt(st) == ' ') {
            st++;
        }
        while (en > st && s.charAt(en - 1) == ' ') {
            en--;
        }
        return s.substring(st, en);
    }
}
