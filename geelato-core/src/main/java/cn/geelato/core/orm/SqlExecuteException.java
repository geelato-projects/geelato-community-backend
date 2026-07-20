package cn.geelato.core.orm;

import cn.geelato.lang.exception.CoreException;
import cn.geelato.lang.exception.ErrorCode;
import lombok.Getter;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Objects;

/**
 * SQL 执行异常。
 * <p>富异常：除错误码外，保留 SQL 语句、参数、数据库错误码、SQL 状态码以及原始异常引用，便于排障。
 * 错误码引用 {@link CoreErrorCodes#SQL_EXECUTE}（docSlug = {@code "sql-execute"}，提供独立在线文档详情页）。</p>
 */
@Getter
public class SqlExecuteException extends CoreException {

    private final String sql;
    private final Object[] params;
    private final int dbErrorCode;
    private final String sqlState;
    private final SQLException originalSqlException;
    private final DataAccessException originalDataAccessException;

    public SqlExecuteException(DataAccessException dae, String sql, Object[] params) {
        super(CoreErrorCodes.SQL_EXECUTE, buildErrorMsg(dae, sql, params), dae);
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

}
