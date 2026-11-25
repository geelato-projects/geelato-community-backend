package cn.geelato.core.sql.provider;

import org.springframework.util.StringUtils;

/**
 * 条件片段的跨方言生成工具。
 *
 * <p>MQL 过滤条件中部分语义（如 fis 的集合成员匹配、JSON 列包含匹配）在 MySQL 上有内建函数，
 * 其他数据库无直接等价物，本类按 {@code dbType}（见
 * {@link MetaBaseSqlProvider#resolveEffectiveDbType} 的多级归一结果）生成等价 SQL：
 * <ul>
 *   <li>mysql：保持内建函数形态（{@code FIND_IN_SET} / {@code JSON_CONTAINS}），与存量行为逐字节一致。</li>
 *   <li>postgresql / oracle / 其他方言：标准 SQL 等价改写（{@code LIKE} 配 {@code ESCAPE}），
 *       不依赖任何数据库对象。</li>
 *   <li>sqlserver：{@code CONCAT} + {@code LIKE} 形态（{@code ||} 非 ANSI 全兼容）。</li>
 * </ul>
 *
 * <p>改写语义约定：fis 值内联写入 SQL 文本（不产生绑定参数，见
 * {@code MetaBaseSqlProvider#recombine} 对 fis 的跳过），MySQL 路径沿用现状不做转义；
 * 其他方言路径对内联值做单引号翻倍与 LIKE 通配符转义（{@code ESCAPE '\'}），
 * 保证"字面量精确元素匹配"语义不漂移。
 */
public final class DialectConditionSql {

    private DialectConditionSql() {
    }

    /**
     * fis 集合成员匹配：值 v 是逗号分隔集合列 col 的成员之一，多值 OR 组合。
     * <ul>
     *   <li>mysql：{@code FIND_IN_SET( 'v',col) >0}</li>
     *   <li>sqlserver：{@code CONCAT(',', col, ',') LIKE '%,v,%' ESCAPE '\'}</li>
     *   <li>其他（postgresql/oracle/未知）：{@code (',' || col || ',') LIKE '%,v,%' ESCAPE '\'}</li>
     * </ul>
     *
     * @param values     集合值数组（原始字面量，未转义）
     * @param columnExpr 列表达式（含别名装饰后的完整形态）
     * @param dbType     归一后的方言（mysql/postgresql/sqlserver/oracle/...）
     * @return 单个条件片段（不含外层括号与 OR 连接符之外的结构，多值由调用方组合时
     *         本方法内部直接返回完整 OR 组合片段）
     */
    public static String renderSetMembership(String[] values, String columnExpr, String dbType) {
        if ("mysql".equalsIgnoreCase(dbType)) {
            StringBuilder sb = new StringBuilder("(  ");
            for (int i = 0; i < values.length; i++) {
                sb.append(String.format(" FIND_IN_SET( '%s',%s) >0", values[i], columnExpr));
                if (i < values.length - 1) {
                    sb.append("  or  ");
                }
            }
            sb.append("  )");
            return sb.toString();
        }
        boolean sqlServer = "sqlserver".equalsIgnoreCase(dbType);
        StringBuilder sb = new StringBuilder("(  ");
        for (int i = 0; i < values.length; i++) {
            String escaped = escapeLikeLiteral(values[i]);
            if (sqlServer) {
                sb.append(String.format(" CONCAT(',', %s, ',') LIKE '%%,%s,%%' ESCAPE '\\'", columnExpr, escaped));
            } else {
                sb.append(String.format(" (',' || %s || ',') LIKE '%%,%s,%%' ESCAPE '\\'", columnExpr, escaped));
            }
            if (i < values.length - 1) {
                sb.append("  or  ");
            }
        }
        sb.append("  )");
        return sb.toString();
    }

    /**
     * JSON 列成员包含：值 v 包含于 JSON 列 col（数组元素或标量值）。
     * <ul>
     *   <li>mysql：{@code JSON_CONTAINS( col->'$','"v"') >0}</li>
     *   <li>postgresql：{@code (col)::jsonb @> ('"v"')::jsonb}</li>
     *   <li>sqlserver / oracle：不支持，抛出 {@link IllegalStateException}。</li>
     * </ul>
     */
    public static String renderJsonContains(String columnExpr, String value, String dbType) {
        if ("mysql".equalsIgnoreCase(dbType)) {
            return String.format(" JSON_CONTAINS( %s->'$','%s') >0", columnExpr, "\"" + value + "\"");
        }
        if ("postgresql".equalsIgnoreCase(dbType)) {
            String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
            return String.format(" (%s)::jsonb @> ('\"%s\"')::jsonb ", columnExpr, escaped);
        }
        throw new IllegalStateException(
                String.format("JSON 列 fis/JSON_CONTAINS 条件仅支持 mysql/postgresql，当前方言：%s", dbType));
    }

    /**
     * 非 MySQL 路径的内联值转义：单引号翻倍（SQL 标准）；LIKE 通配符
     * {@code %}、{@code _} 与转义符 {@code \} 前置 {@code \}，配合
     * {@code ESCAPE '\'} 保证字面量语义。
     */
    static String escapeLikeLiteral(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String s = value.replace("'", "''");
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
