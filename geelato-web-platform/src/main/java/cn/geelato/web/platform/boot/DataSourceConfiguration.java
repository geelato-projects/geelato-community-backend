package cn.geelato.web.platform.boot;

import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.core.orm.Dao;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;


/**
 * @author geelato
 */
@Configuration
public class DataSourceConfiguration extends BaseConfiguration {


    @Bean(name = "primaryDataSource")
    @Qualifier("primaryDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.primary")
    public DataSource primaryDataSource() {
        return DataSourceBuilder.create().build();
    }
    @Bean(name = "primaryJdbcTemplate")
    public JdbcTemplate primaryJdbcTemplate(@Qualifier("primaryDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
    @Bean(name = "primaryDao")
    public Dao primaryDao(@Qualifier("primaryJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return new Dao(jdbcTemplate);
    }

    @Bean(name = "dynamicDataSource")
    @Qualifier("dynamicDataSource")
    public DataSource dynamicDataSource() {
        DynamicDataSource dynamicDatasource=new DynamicDataSource();
        DataSourceManager.singleInstance().parseDataSourceMeta(primaryDao(primaryJdbcTemplate(primaryDataSource())));
        Map<Object, Object> dymanicDataSourceMap=DataSourceManager.singleInstance().getDynamicDataSourceMap();
        dymanicDataSourceMap.put("primary",primaryDataSource());
        dynamicDatasource.setTargetDataSources(dymanicDataSourceMap);
        dynamicDatasource.setDefaultTargetDataSource(primaryDataSource());
        return dynamicDatasource;
    }

    @Bean(name = "dynamicJdbcTemplate")
    public JdbcTemplate dynamicJdbcTemplate(@Qualifier("dynamicDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "dynamicDao")
    public Dao dynamicDao(@Qualifier("dynamicJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return new Dao(jdbcTemplate);
    }

    @Bean(name = "dbGenerateDao")
    public Dao DbGenerateDao(@Qualifier("primaryJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return new Dao(jdbcTemplate);
    }
}
