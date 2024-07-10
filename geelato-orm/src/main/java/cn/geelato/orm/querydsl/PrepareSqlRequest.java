package cn.geelato.orm.querydsl;


public class PrepareSqlRequest implements SqlRequest {
    private String sql;
    private Object[] parameters;

    @Override
    public String getSql() {
        return sql;
    }

    @Override
    public Object[] getParameters() {
        return parameters;
    }
}
