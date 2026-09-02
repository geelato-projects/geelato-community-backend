package cn.geelato.core.orm;

import org.springframework.dao.DataAccessException;

/**
 * SQL 锁冲突异常（10022）：死锁 / 锁等待超时。
 * <p>判定：MySQL 1213（死锁）/ 1205（锁等待超时）、标准 sqlState {@code 40001}、
 * PG {@code 40P01}（死锁）/ {@code 55P03}（锁不可用，NOWAIT / lock_timeout 场景）。
 * 由 {@link SqlExecuteException#of} 分类工厂创建。</p>
 */
public class SqlLockConflictException extends SqlExecuteException {

    public static final int ERROR_CODE = 10022;

    public SqlLockConflictException(DataAccessException dae, String sql, Object[] params) {
        super(ERROR_CODE, dae, sql, params);
    }

    @Override
    public String getUserMessage() {
        return "当前数据正被其他操作占用，请稍后重试";
    }
}
