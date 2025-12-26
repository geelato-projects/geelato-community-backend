package cn.geelato.core.orm;

import lombok.Getter;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Objects;

@Getter
public class SqlExecuteException extends RuntimeException{
    private final String sql;
    private final Object[] params;
    private final int dbErrorCode;
    private final String sqlState;
    private final SQLException originalSqlException;
    private final DataAccessException originalDataAccessException;
        public SqlExecuteException(DataAccessException dae, String sql, Object[] params) {
        super(buildErrorMsg(dae, sql, params), dae);
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
        StringBuilder sb = new StringBuilder();
        sb.append("SQL执行异常").append(ls)
          .append("原因：").append(Objects.requireNonNullElse(dae.getMessage(), "")).append(ls)
          .append("执行SQL：").append(Objects.requireNonNullElse(sql, "")).append(ls)
          .append("参数：").append(params == null ? "[]" : Arrays.toString(params)).append(ls)
          .append("数据库错误码：").append(dbErrorCode).append(ls)
          .append("SQL状态码：").append(Objects.requireNonNullElse(sqlState, ""));
        return sb.toString();

    }

}
