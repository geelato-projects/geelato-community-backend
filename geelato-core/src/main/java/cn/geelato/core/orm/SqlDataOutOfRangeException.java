package cn.geelato.core.orm;

import org.springframework.dao.DataAccessException;

/**
 * SQL 数值超出范围异常（10026）：数值超出字段允许的范围。
 * <p>判定：MySQL 1690（Out of range value）、标准/PG sqlState {@code 22003}（numeric_value_out_of_range）。
 * 由 {@link SqlExecuteException#of} 分类工厂创建；用户文案携带从根因消息提取的字段名（MySQL 可提取）。</p>
 */
public class SqlDataOutOfRangeException extends SqlExecuteException {

    public static final int ERROR_CODE = 10026;

    public SqlDataOutOfRangeException(DataAccessException dae, String sql, Object[] params) {
        super(ERROR_CODE, dae, sql, params);
    }

    @Override
    public String getUserMessage() {
        String column = extractColumnName();
        return column != null
                ? "字段[" + column + "]的数值超出允许范围，请调整后重试"
                : "数值超出字段允许范围，请调整后重试";
    }
}
