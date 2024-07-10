package cn.geelato.orm.gql.execute;

/**
 * @author geemeta
 *
 */
public class BoundPageSql{

    private BoundSql boundSql;

    private String countSql;

    public BoundSql getBoundSql() {
        return boundSql;
    }

    public void setBoundSql(BoundSql boundSql) {
        this.boundSql = boundSql;
    }

    /**
     * @return 统计总数的sql，如select count(id) from t1 where ...
     */
    public String getCountSql() {
        return countSql;
    }

    public void setCountSql(String countSql) {
        this.countSql = countSql;
    }
}
