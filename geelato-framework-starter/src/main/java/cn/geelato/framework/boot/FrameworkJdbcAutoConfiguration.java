package cn.geelato.framework.boot;

import cn.geelato.core.orm.Dao;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring.datasource.primary", name = "jdbc-url")
public class FrameworkJdbcAutoConfiguration {

    @Bean(name = "primaryDataSource")
    @ConditionalOnMissingBean(name = "primaryDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.primary")
    public DataSource primaryDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "primaryJdbcTemplate")
    @ConditionalOnMissingBean(name = "primaryJdbcTemplate")
    public JdbcTemplate primaryJdbcTemplate(@Qualifier("primaryDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "primaryDao")
    @ConditionalOnMissingBean(name = "primaryDao")
    public Dao primaryDao(@Qualifier("primaryJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return new Dao(jdbcTemplate);
    }

    @Bean(name = "dbGenerateDao")
    @ConditionalOnMissingBean(name = "dbGenerateDao")
    public Dao dbGenerateDao(@Qualifier("primaryJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return new Dao(jdbcTemplate);
    }

    @Bean(name = "secondaryDataSource")
    @ConditionalOnMissingBean(name = "secondaryDataSource")
    @ConditionalOnProperty(prefix = "spring.datasource.secondary", name = "jdbc-url")
    @ConfigurationProperties(prefix = "spring.datasource.secondary")
    public DataSource secondaryDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "secondaryJdbcTemplate")
    @ConditionalOnMissingBean(name = "secondaryJdbcTemplate")
    @ConditionalOnBean(name = "secondaryDataSource")
    public JdbcTemplate secondaryJdbcTemplate(@Qualifier("secondaryDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "secondaryDao")
    @ConditionalOnMissingBean(name = "secondaryDao")
    @ConditionalOnBean(name = "secondaryJdbcTemplate")
    public Dao secondaryDao(@Qualifier("secondaryJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return new Dao(jdbcTemplate);
    }
}
