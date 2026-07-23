package cn.geelato.datasource;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;


import javax.sql.DataSource;

/**
 * 动态数据源配置类
 *
 * <p>注：基于 platform_dev_db_connect 的默认 {@code DynamicDataSourceDefinitionLoader} 实现已迁至
 * 业务层（geelato-web-platform 的 {@code cn.geelato.datasource.PlatformDynamicDataSourceDefinitionLoader}），
 * 框架层不再绑定具体数据库表。</p>
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
}
