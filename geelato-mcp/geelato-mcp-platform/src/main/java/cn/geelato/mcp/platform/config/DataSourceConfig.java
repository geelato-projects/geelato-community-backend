package cn.geelato.mcp.platform.config;

import cn.geelato.core.orm.Dao;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 数据源配置
 * 创建 primaryDataSource、primaryJdbcTemplate 和 primaryDao
 */
@Configuration
public class DataSourceConfig {

    @Value("${spring.datasource.primary.jdbc-url:}")
    private String jdbcUrl;

    @Value("${spring.datasource.primary.username:}")
    private String username;

    @Value("${spring.datasource.primary.password:}")
    private String password;

    @Value("${spring.datasource.primary.driver-class-name:com.mysql.cj.jdbc.Driver}")
    private String driverClassName;

    @Bean(name = "primaryDataSource")
    @Qualifier("primaryDataSource")
    public DataSource primaryDataSource() {
        if (jdbcUrl == null || jdbcUrl.isEmpty()) {
            throw new IllegalStateException("数据库URL未配置，请设置 spring.datasource.primary.jdbc-url");
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        return new HikariDataSource(config);
    }

    @Bean(name = "primaryJdbcTemplate")
    @Qualifier("primaryJdbcTemplate")
    public JdbcTemplate primaryJdbcTemplate(@Qualifier("primaryDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "primaryDao")
    @Qualifier("primaryDao")
    public Dao primaryDao(@Qualifier("primaryJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return new Dao(jdbcTemplate);
    }
}
