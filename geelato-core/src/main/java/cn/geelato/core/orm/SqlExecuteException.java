package cn.geelato.core.orm;

import cn.geelato.lang.exception.CoreException;
import lombok.Getter;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Objects;

@Getter
public class SqlExecuteException extends CoreException {

    private static final int DEFAULT_CODE = 10010;

    private final String sql;
    private final Object[] params;
    private final int dbErrorCode;
    private final String sqlState;
    private final SQLException originalSqlException;
    private final DataAccessException originalDataAccessException;
        public SqlExecuteException(DataAccessException dae, String sql, Object[] params) {
        super(DEFAULT_CODE,buildErrorMsg(dae, sql, params), dae);
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
