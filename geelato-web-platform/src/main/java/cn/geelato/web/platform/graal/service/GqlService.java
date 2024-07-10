package cn.geelato.web.platform.graal.service;

import cn.geelato.web.platform.m.base.service.RuleService;
import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.core.graal.GraalService;
import cn.geelato.core.orm.Dao;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@GraalService(name="dao",built = "true")
public class GqlService extends RuleService {
    public GqlService(){
        setDao(initDefaultDao());
    }

    private Dao initDefaultDao() {
        DataSource ds= (DataSource) DataSourceManager.singleInstance().getDynamicDataSourceMap().get("primary");
        JdbcTemplate jdbcTemplate=new JdbcTemplate();
        jdbcTemplate.setDataSource(ds);
        return new Dao(jdbcTemplate);
    }
}
