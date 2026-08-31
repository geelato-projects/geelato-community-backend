package cn.geelato.core.orm;

import cn.geelato.lang.exception.CoreException;
import lombok.Getter;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * SQL 执行异常。
 * <p>富异常：除错误码外，保留 SQL 语句、参数、数据库错误码、SQL 状态码以及原始异常引用，便于排障。
 * docSlug = {@code "sql-execute"}（{@link #getDocSlug()} 覆写），提供独立在线文档详情页。</p>
 */
@Getter
public class SqlExecuteException extends CoreException {

    public static final int ERROR_CODE = 10002;

    private final String sql;
    private final Object[] params;
    private final int dbErrorCode;
    private final String sqlState;
    private final SQLException originalSqlException;
    private final DataAccessException originalDataAccessException;

    public SqlExecuteException(DataAccessException dae, String sql, Object[] params) {
        super(ERROR_CODE, buildErrorMsg(dae, sql, params), dae);
        Throwable rootCause = NestedExceptionUtils.getRootCause(dae);
        SQLException sqlException = rootCause instanceof SQLException ? (SQLException) rootCause : null;
        this.sql = sql;
        this.params = params;
        this.dbErrorCode = sqlException != null ? sqlException.getErrorCode() : -1;
        this.sqlState = sqlException != null ? sqlException.getSQLState() : null;
        this.originalSqlException = sqlException;
        this.originalDataAccessException = dae;
    }

    public SqlExecuteException(DataAccessException dae, String sql) {
        this(dae, sql, null);
    }

    private static String buildErrorMsg(DataAccessException dae, String sql, Object[] params) {
        Throwable rootCause = NestedExceptionUtils.getRootCause(dae);
        SQLException sqlException = rootCause instanceof SQLException ? (SQLException) rootCause : null;
        int dbErrorCode = sqlException != null ? sqlException.getErrorCode() : -1;
        String sqlState = sqlException != null ? sqlException.getSQLState() : null;
        String ls = System.lineSeparator();
        return "SQL执行异常" + ls +
                "原因：" + Objects.requireNonNullElse(dae.getMessage(), "") + ls +
                "执行SQL：" + Objects.requireNonNullElse(sql, "") + ls +
                "参数：" + (params == null ? "[]" : Arrays.toString(params)) + ls +
                "数据库错误码：" + dbErrorCode + ls +
                "SQL状态码：" + Objects.requireNonNullElse(sqlState, "");

    }

    @Override
    public String getDocSlug() {
        return "sql-execute";
    }

    /**
     * 前端用户可见的友好文案：按根因 / sqlState / 数据库错误码分类，不含 SQL 语句与参数等技术详情。
     * <p>技术详情仅保留在 {@link #getErrorMsg()} 与服务端日志（logTag 关联）中，不再下发前端。</p>
     */
    @Override
    public String getUserMessage() {
        if (isConnectionFailure()) {
            return "数据库连接中断，系统正在自动恢复，请稍后重试";
        }
        // 死锁 / 锁等待超时（MySQL 1213/1205、标准 sqlState 40001、PG 40P01）
        if (dbErrorCode == 1213 || dbErrorCode == 1205
                || "40001".equals(sqlState) || "40P01".equals(sqlState)) {
            return "当前数据正被其他操作占用，请稍后重试";
        }
        // 唯一键冲突（MySQL 1062、PG 23505）
        if (dbErrorCode == 1062 || "23505".equals(sqlState)) {
            return "数据已存在，无法重复提交";
        }
        // 外键 / 其他完整性约束（MySQL 1451/1452、PG 及标准 sqlState 23xxx 段）
        if (dbErrorCode == 1451 || dbErrorCode == 1452
                || (sqlState != null && sqlState.startsWith("23"))) {
            return "数据存在关联引用或不符合约束，请检查后重试";
        }
        return "数据操作失败，请稍后重试";
    }

    private boolean isConnectionFailure() {
        if (originalDataAccessException instanceof CannotGetJdbcConnectionException) {
            return true;
        }
        if (sqlState != null && sqlState.startsWith("08")) {
            return true;
        }
        if (originalSqlException != null && originalSqlException.getMessage() != null) {
            String lower = originalSqlException.getMessage().toLowerCase(Locale.ROOT);
            return lower.contains("communications link failure") || lower.contains("connection refused");
        }
        return false;
    }

}
