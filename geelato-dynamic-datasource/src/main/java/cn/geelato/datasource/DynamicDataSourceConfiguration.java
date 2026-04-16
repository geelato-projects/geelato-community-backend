package cn.geelato.datasource;


import com.atomikos.jdbc.AtomikosDataSourceBean;
import com.mysql.cj.jdbc.MysqlXADataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;


import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 动态数据源配置类
 */
@Configuration
public class DynamicDataSourceConfiguration {
    
    @Autowired
    private DynamicDataSourceRegistry dataSourceRegistry;

    /**
     * 动态数据源
     */
    @Bean(name = "dynamicDataSource")
    public DynamicRoutingDataSource dynamicDataSource() {
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
