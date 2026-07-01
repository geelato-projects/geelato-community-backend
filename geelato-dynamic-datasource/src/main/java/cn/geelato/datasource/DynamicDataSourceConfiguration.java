package cn.geelato.datasource;


import cn.geelato.datasource.spi.DynamicDataSourceDefinitionLoader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;


import javax.sql.DataSource;

/**
 * 动态数据源配置类
 */
@Configuration
public class DynamicDataSourceConfiguration {

    /**
     * 动态数据源
     */
    @Bean(name = "dynamicDataSource")
    public DynamicRoutingDataSource dynamicDataSource(DynamicDataSourceRegistry dataSourceRegistry) {
        DynamicRoutingDataSource dynamicDataSource = new DynamicRoutingDataSource();
        dynamicDataSource.setDynamicDataSourceRegistry(dataSourceRegistry);
        dynamicDataSource.refreshAllDataSources();
        return dynamicDataSource;
    }

    /**
     * 动态数据源JdbcTemplate
     */
    @Bean(name = "dynamicJdbcTemplate")
    public JdbcTemplate dynamicJdbcTemplate(@Qualifier("dynamicDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean(DynamicDataSourceDefinitionLoader.class)
    public DynamicDataSourceDefinitionLoader dynamicDataSourceDefinitionLoader(@Qualifier("primaryJdbcTemplate") JdbcTemplate primaryJdbcTemplate) {
        return new PlatformDynamicDataSourceDefinitionLoader(primaryJdbcTemplate);
    }
}
