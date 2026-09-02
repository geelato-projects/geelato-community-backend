package cn.geelato.core.orm;

import org.springframework.dao.DataAccessException;

/**
 * SQL 约束违反异常（10024）：外键引用 / 完整性约束失败。
 * <p>判定：MySQL 1451/1452（外键）、标准 sqlState {@code 23xxx} 段
 * （PG {@code 23503} 外键、{@code 23502} 非空、{@code 23514} CHECK 等）。
 * 由 {@link SqlExecuteException#of} 分类工厂创建。</p>
 */
public class SqlConstraintViolationException extends SqlExecuteException {

    public static final int ERROR_CODE = 10024;

    public SqlConstraintViolationException(DataAccessException dae, String sql, Object[] params) {
        super(ERROR_CODE, dae, sql, params);
    }

    @Override
    public String getUserMessage() {
        return "数据存在关联引用或不符合约束，请检查后重试";
    }
}
