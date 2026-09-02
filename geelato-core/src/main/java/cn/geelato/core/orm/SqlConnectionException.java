package cn.geelato.core.orm;

import org.springframework.dao.DataAccessException;

/**
 * SQL 连接异常（10021）：数据库连接中断 / 通信失败。
 * <p>判定：{@link org.springframework.jdbc.CannotGetJdbcConnectionException}、sqlState {@code 08xxx}
 * （PG 08001/08003/08006 等）、根因消息含 {@code Communications link failure} / {@code Connection refused}（MySQL 断连典型消息）。
 * 由 {@link SqlExecuteException#of} 分类工厂创建；docSlug 与技术详情字段继承根类。</p>
 */
public class SqlConnectionException extends SqlExecuteException {

    public static final int ERROR_CODE = 10021;

    public SqlConnectionException(DataAccessException dae, String sql, Object[] params) {
        super(ERROR_CODE, dae, sql, params);
    }

    @Override
    public String getUserMessage() {
        return "数据库连接中断，系统正在自动恢复，请稍后重试";
    }
}
