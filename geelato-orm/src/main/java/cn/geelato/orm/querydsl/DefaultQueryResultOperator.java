package cn.geelato.orm.querydsl;

import cn.geelato.orm.utils.ExceptionUtil;

import java.util.function.Supplier;

public class DefaultQueryResultOperator<E, R> implements QueryResultOperator<E, R>{
    private final Supplier<SqlRequest> sqlRequest;
    private final ResultWrapper<E, R> wrapper;

    private final RDBDatabaseMetadata metadata;

    public DefaultQueryResultOperator(Supplier<SqlRequest> sqlRequest,
                                      TableOrViewMetadata tableOrViewMetadata,
                                      ResultWrapper<E, R> wrapper) {
        this.sqlRequest = sqlRequest;
        this.metadata = tableOrViewMetadata.getSchema().getDatabase();
        this.wrapper = wrapper;
    }

    protected ResultWrapper<E, R> getWrapper() {
        return wrapper;
    }
    @Override
    public R sync() {
        return ExceptionUtil
                .translation(() -> metadata
                        .findFeatureNow(SyncSqlExecutor.ID)
                        .select(sqlRequest.get(), getWrapper()), metadata);
    }
}
