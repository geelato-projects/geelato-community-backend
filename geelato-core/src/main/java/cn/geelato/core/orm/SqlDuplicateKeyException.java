package cn.geelato.core.orm;

import org.springframework.dao.DataAccessException;

/**
 * SQL 唯一键冲突异常（10023）：主键 / 唯一约束重复。
 * <p>判定：MySQL 1062（Duplicate entry）、PG {@code 23505}（unique_violation）。
 * 由 {@link SqlExecuteException#of} 分类工厂创建。</p>
 */
public class SqlDuplicateKeyException extends SqlExecuteException {

    public static final int ERROR_CODE = 10023;

    public SqlDuplicateKeyException(DataAccessException dae, String sql, Object[] params) {
        super(ERROR_CODE, dae, sql, params);
    }

    @Override
    public String getUserMessage() {
        return "数据已存在，无法重复提交";
    }
}
