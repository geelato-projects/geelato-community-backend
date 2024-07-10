package cn.geelato.orm.querydsl;

import java.sql.Connection;

public abstract class DefaultSyncSqlExecutor extends DefaultSqlExecutor implements SyncSqlExecutor {
    public abstract Connection getConnection(SqlRequest sqlRequest);

    public abstract void releaseConnection(Connection connection, SqlRequest sqlRequest);

    @Override
    public <T, R> R select(SqlRequest request, ResultWrapper<T, R> wrapper) {
        Connection connection = getConnection(request);
        try {
            return doSelect(connection, request, wrapper);
        } finally {
            releaseConnection(connection, request);
        }
    }
}
