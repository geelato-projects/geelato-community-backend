package cn.geelato.core.orm;

import org.springframework.dao.DataAccessException;

/**
 * SQL 数据格式不正确异常（10027）：值与字段类型/格式不匹配。
 * <p>判定：MySQL 1366（Incorrect string value，字符集/非法值，sqlState 为 HY000 故按厂商码判定）、
 * 1292（Incorrect date value）、标准/PG sqlState {@code 22007}/{@code 22008}（非法日期时间）。
 * 由 {@link SqlExecuteException#of} 分类工厂创建；用户文案携带从根因消息提取的字段名（MySQL 可提取）。</p>
 */
public class SqlDataFormatException extends SqlExecuteException {

    public static final int ERROR_CODE = 10027;

    public SqlDataFormatException(DataAccessException dae, String sql, Object[] params) {
        super(ERROR_CODE, dae, sql, params);
    }

    @Override
    public String getUserMessage() {
        String column = extractColumnName();
        return column != null
                ? "字段[" + column + "]的数据格式不正确，请检查填写内容后重试"
                : "数据格式不正确（如日期格式、特殊字符），请检查后重试";
    }
}
