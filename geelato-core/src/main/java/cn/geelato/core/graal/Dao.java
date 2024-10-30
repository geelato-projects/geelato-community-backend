package cn.geelato.core.graal;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.core.gql.GqlManager;
import cn.geelato.core.sql.SqlManager;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

public class Dao {
    private final GqlManager gqlManager = GqlManager.singleInstance();
    private final SqlManager sqlManager = SqlManager.singleInstance();
    private cn.geelato.core.orm.Dao ormDao;

    public Dao(){
        DataSource ds= DataSourceManager.singleInstance().getDataSource("primary");
        JdbcTemplate jdbcTemplate=new JdbcTemplate();
        jdbcTemplate.setDataSource(ds);
//        this.ormDao=new cn.geelato.core.orm.Dao(jdbcTemplate);
    }



    public String save(String gql){
        return null;
    }

    public String delete(String gql){
        return null;
    }
    private SessionCtx getSessionCtx() {
        return new SessionCtx();
    }
}
