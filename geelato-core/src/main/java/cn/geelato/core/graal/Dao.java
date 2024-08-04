package cn.geelato.core.graal;

import cn.geelato.core.Ctx;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.core.gql.GqlManager;
import cn.geelato.core.gql.execute.BoundPageSql;
import cn.geelato.core.gql.parser.QueryCommand;
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
    private Ctx getSessionCtx() {
        return new Ctx();
    }
}
