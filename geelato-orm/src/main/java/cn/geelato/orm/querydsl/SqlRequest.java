package cn.geelato.orm.querydsl;

public interface SqlRequest {
    String getSql();
    Object[] getParameters();
    boolean isNotEmpty();
}
