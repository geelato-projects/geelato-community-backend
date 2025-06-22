package cn.geelato.datasource;


import com.atomikos.jdbc.AtomikosDataSourceBean;
import com.mysql.cj.jdbc.MysqlXADataSource;
import org.apache.seata.rm.datasource.DataSourceProxy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;


import javax.sql.DataSource;
import javax.sql.XADataSource;
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
     * 主数据源配置属性
     * 支持 spring.datasource 或 spring.datasource.primary 配置
     */
    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }
    
    /**
     * 备用主数据源配置属性（当使用 spring.datasource.primary 时）
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.primary")
    public DataSourceProperties primaryDataSourcePropertiesAlternative() {
        return new DataSourceProperties();
    }
    
    /**
     * 主数据源
     * 优先使用 spring.datasource.primary 配置，如果不存在则使用 spring.datasource 配置
     * 只有在上游模块没有创建 primaryDataSource 时才创建
     */
    @Bean(name = "primaryDataSource")
    @Primary
    @ConditionalOnMissingBean(name = "primaryDataSource")
    public DataSource primaryDataSource() {
        DataSourceProperties properties = primaryDataSourcePropertiesAlternative();
        if (properties.getUrl() == null || properties.getUrl().isEmpty()) {
            properties = primaryDataSourceProperties();
        }
        AtomikosDataSourceBean ds = new AtomikosDataSourceBean();
        ds.setUniqueResourceName("primary");
        ds.setXaDataSource(properties.initializeDataSourceBuilder().type(MysqlXADataSource.class).build());
        return ds;
    }
    
    /**
     * 主数据源JdbcTemplate
     * 只有在上游模块没有创建 primaryJdbcTemplate 时才创建
     */
    @Bean(name = "primaryJdbcTemplate")
    @Primary
    @ConditionalOnMissingBean(name = "primaryJdbcTemplate")
    public JdbcTemplate primaryJdbcTemplate(@Qualifier("primaryDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * 动态数据源
     */
    @Bean(name = "dynamicDataSource")
    public DataSource dynamicDataSource(@Qualifier("primaryDataSource") DataSource primaryDataSource) {
        DynamicDataSourceRoute dynamicDataSource = new DynamicDataSourceRoute();
        dynamicDataSource.setDynamicDataSourceRegistry(dataSourceRegistry);
        // 设置目标数据源映射
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put("primary", primaryDataSource);
        
        // 添加注册器中的所有数据源
        Map<String, DataSource> registryDataSources = dataSourceRegistry.getAllDataSources();
        targetDataSources.putAll(registryDataSources);
        
        dynamicDataSource.setTargetDataSources(targetDataSources);
        dynamicDataSource.setDefaultTargetDataSource(primaryDataSource);
        
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
