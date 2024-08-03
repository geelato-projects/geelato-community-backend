package cn.geelato.orm.query;


import java.util.List;

public abstract class QueryOperator extends BaseOperator implements QueryOP {
    public abstract QueryOperator select(String... columns);
    public abstract QueryOperator where(String... conditions);
    public QueryOperator(String table){

    }

    @Override
    void buildSql() {
        this.sql="select * from "+this.getFrom();
    }

    @Override
    public <T> List<T> pageQueryList() {
        return List.of();
    }
}
