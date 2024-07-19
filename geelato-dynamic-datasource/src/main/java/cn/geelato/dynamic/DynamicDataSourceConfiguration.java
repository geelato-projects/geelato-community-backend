package cn.geelato.dynamic;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.Map;

@Configuration
public class DynamicDataSourceConfiguration {

    AbstractDynamicDataSourceManager dynamicDataSourceManager;
    DataSource primaryDataSource;

    public DynamicDataSourceConfiguration(){
        dynamicDataSourceManager.preLoadDataSourceMap();
    }

    @Bean(name = "dynamicDataSource")
    @Qualifier("dynamicDataSource")
    public DataSource dynamicDataSource() {
        AbstractRoutingDataSource dynamicDatasource=new DynamicDataSource();
        Map<Object, Object> dymanicDataSourceMap=dynamicDataSourceManager.getDynamicDataSourceMap();
        dynamicDatasource.setTargetDataSources(dymanicDataSourceMap);
        dynamicDatasource.setDefaultTargetDataSource(primaryDataSource);
        return dynamicDatasource;
    }

    @Bean(name = "dynamicJdbcTemplate")
    public JdbcTemplate dynamicJdbcTemplate(@Qualifier("dynamicDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
