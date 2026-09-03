package cn.geelato.core.orm;

import org.springframework.dao.DataAccessException;

/**
 * SQL 数据超长异常（10025）：内容超出字段/列的长度限制。
 * <p>判定：MySQL 1406（Data truncation: Data too long for column）、标准/PG sqlState {@code 22001}
 * （string_data_right_truncation）。由 {@link SqlExecuteException#of} 分类工厂创建；
 * 用户文案携带从根因消息提取的字段名（MySQL 可提取，PG 消息通常不含字段名则给通用文案）。</p>
 */
public class SqlDataTooLongException extends SqlExecuteException {

    public static final int ERROR_CODE = 10025;

    public SqlDataTooLongException(DataAccessException dae, String sql, Object[] params) {
        super(ERROR_CODE, dae, sql, params);
    }

    @Override
    public String getUserMessage() {
        String column = extractColumnName();
        return column != null
                ? "字段[" + column + "]的内容超出长度限制，请缩短后重试"
                : "数据内容超出字段长度限制，请缩短后重试";
    }
}
