package cn.geelato.orm.querydsl;

import cn.geelato.orm.querydsl.ResultWrapper;
import cn.geelato.orm.querydsl.SqlRequest;

public interface SyncSqlExecutor {
    String ID_VALUE = "syncSqlExecutor";

    FeatureId<SyncSqlExecutor> ID = FeatureId.of(ID_VALUE);
    <T, R> R select(SqlRequest request, ResultWrapper<T, R> wrapper);
}
