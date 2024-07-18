package cn.geelato.orm.querydsl;

public abstract class QueryOperator {
    public abstract QueryOperator select(String... columns);
    public abstract QueryOperator where(String... conditions);

}
