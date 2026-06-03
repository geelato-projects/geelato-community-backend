package cn.geelato.orm.query;

public enum JoinType {
    INNER("inner join"),
    LEFT("left join"),
    RIGHT("right join");

    private final String sqlKeyword;

    JoinType(String sqlKeyword) {
        this.sqlKeyword = sqlKeyword;
    }

    public String getSqlKeyword() {
        return sqlKeyword;
    }
}
