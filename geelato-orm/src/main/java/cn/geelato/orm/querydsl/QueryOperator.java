package cn.geelato.orm.querydsl;

public abstract class QueryOperator {
    public abstract QueryOperator select(String... columns);
    public abstract QueryOperator where(String... conditions);
    public abstract <E, R> QueryResultOperator<E, R> fetch(ResultWrapper<E, R> wrapper);

}
