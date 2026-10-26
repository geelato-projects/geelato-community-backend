package cn.geelato.metasync.fix;

/**
 * 参数化 SQL 语句：{@code sql} 用 {@code ?} 占位，{@code params} 为绑定值。
 * <p>
 * 执行统一走 {@code JdbcTemplate.update(sql, params)}（PreparedStatement），值永不拼进 SQL 文本，
 * 从根上杜绝拼接注入（原实现仅靠 {@code ''} 转义，遇到反斜杠/多字节转义序列仍可被绕过）。
 * <p>
 * {@link #toPreview()} 只用于 dry-run 预览展示（把 {@code ?} 渲染成可读字面量），
 * <b>永不用于执行</b>，故其转义仅为显示美观，不承担安全职责。
 *
 * @author geemeta
 */
public class SqlStatement {

    private final String sql;
    private final Object[] params;

    public SqlStatement(String sql, Object... params) {
        this.sql = sql;
        this.params = params == null ? new Object[0] : params;
    }

    public String getSql() {
        return sql;
    }

    public Object[] getParams() {
        return params;
    }

    /**
     * 仅展示：按顺序把 {@code ?} 替换为绑定值字面量（数值/布尔原样、null→NULL、其余加单引号并转义）。
     */
    public String toPreview() {
        StringBuilder out = new StringBuilder();
        int pi = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '?' && pi < params.length) {
                out.append(literal(params[pi++]));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String literal(Object v) {
        if (v == null) {
            return "NULL";
        }
        if (v instanceof Number || v instanceof Boolean) {
            return v.toString();
        }
        return "'" + v.toString().replace("'", "''") + "'";
    }
}
