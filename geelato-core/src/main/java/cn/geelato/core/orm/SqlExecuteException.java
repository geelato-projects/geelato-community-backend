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
 * SQL 执行异常（根码 10002）。
 * <p>富异常：除错误码外，保留 SQL 语句、参数、数据库错误码、SQL 状态码以及原始异常引用，便于排障。
 * docSlug = {@code "sql-execute"}（{@link #getDocSlug()} 覆写），提供独立在线文档详情页。</p>
 *
 * <h3>子类细分（1002x 段）</h3>
 * <p>常见可归类的 SQL 故障由 {@link #of(DataAccessException, String, Object[])} 工厂按根因包装为子类，
 * 各持独立错误码；本类兜底未分类的 SQL 错误（如语法错误）：</p>
 * <ul>
 *   <li>{@link SqlConnectionException}（10021）—— 连接中断 / 通信失败</li>
 *   <li>{@link SqlLockConflictException}（10022）—— 死锁 / 锁等待超时</li>
 *   <li>{@link SqlDuplicateKeyException}（10023）—— 唯一键冲突</li>
 *   <li>{@link SqlConstraintViolationException}（10024）—— 外键 / 完整性约束</li>
 * </ul>
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
        this(ERROR_CODE, dae, sql, params);
    }

    public SqlExecuteException(DataAccessException dae, String sql) {
        this(dae, sql, null);
    }

    /** 供子类传入各自错误码的构造器。 */
    protected SqlExecuteException(int errorCode, DataAccessException dae, String sql, Object[] params) {
        super(errorCode, buildErrorMsg(dae, sql, params), dae);
        SQLException sqlException = rootSQLException(dae);
        this.sql = sql;
        this.params = params;
        this.dbErrorCode = sqlException != null ? sqlException.getErrorCode() : -1;
        this.sqlState = sqlException != null ? sqlException.getSQLState() : null;
        this.originalSqlException = sqlException;
        this.originalDataAccessException = dae;
    }

    /**
     * 分类工厂：按根因（连接中断/锁冲突/唯一键冲突/约束违反）实例化对应子类（10021-10024），
     * 未归类（语法错误、权限不足等）返回根类自身（10002）。Dao 层统一经本工厂包装。
     */
    public static SqlExecuteException of(DataAccessException dae, String sql, Object[] params) {        SQLException root = rootSQLException(dae);
        if (isConnectionFailure(dae, root)) {
            return new SqlConnectionException(dae, sql, params);
        }
        int dbErrorCode = root != null ? root.getErrorCode() : -1;
        String sqlState = root != null ? root.getSQLState() : null;
        // 死锁 / 锁等待超时（MySQL 1213/1205；标准 sqlState 40001；PG 40P01 死锁、55P03 锁不可用/超时）
        // 注意 PG 的 getErrorCode() 恒为 0，判定必须依赖 sqlState
        if (dbErrorCode == 1213 || dbErrorCode == 1205
                || "40001".equals(sqlState) || "40P01".equals(sqlState) || "55P03".equals(sqlState)) {
            return new SqlLockConflictException(dae, sql, params);
        }
        // 唯一键冲突（MySQL 1062、PG 23505）
        if (dbErrorCode == 1062 || "23505".equals(sqlState)) {
            return new SqlDuplicateKeyException(dae, sql, params);
        }
        // 外键 / 其他完整性约束（MySQL 1451/1452、PG 及标准 sqlState 23xxx 段）
        if (dbErrorCode == 1451 || dbErrorCode == 1452
                || (sqlState != null && sqlState.startsWith("23"))) {
            return new SqlConstraintViolationException(dae, sql, params);
        }
        return new SqlExecuteException(dae, sql, params);
    }

    /** @see #of(DataAccessException, String, Object[]) */
    public static SqlExecuteException of(DataAccessException dae, String sql) {
        return of(dae, sql, null);
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

    static SQLException rootSQLException(DataAccessException dae) {
        Throwable rootCause = NestedExceptionUtils.getRootCause(dae);
        return rootCause instanceof SQLException ? (SQLException) rootCause : null;
    }

    static boolean isConnectionFailure(DataAccessException dae, SQLException root) {
        if (dae instanceof CannotGetJdbcConnectionException) {
            return true;
        }
        String sqlState = root != null ? root.getSQLState() : null;
        if (sqlState != null && sqlState.startsWith("08")) {
            return true;
        }
        if (root != null && root.getMessage() != null) {
            String lower = root.getMessage().toLowerCase(Locale.ROOT);
            return lower.contains("communications link failure") || lower.contains("connection refused");
        }
        return false;
    }

    @Override
    public String getDocSlug() {
        return "sql-execute";
    }

    /**
     * 前端用户可见的友好文案：不含 SQL 语句与参数等技术详情。
     * <p>技术详情仅保留在 {@link #getErrorMsg()} 与服务端日志（logTag 关联）中，不下发前端。</p>
     * 根类兜底未分类错误；可归类故障由子类覆写为各自的分类文案。</p>
     */
    @Override
    public String getUserMessage() {
        return "数据操作失败，请稍后重试";
    }

}
