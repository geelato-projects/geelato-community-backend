package cn.geelato.mcp.fms.config;

import cn.geelato.core.orm.Dao;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class FmsDataSourceConfig {

    @Value("${spring.datasource.primary.jdbc-url:${GEELATO_PRIMARY_JDBCURL:}}")
    private String jdbcUrl;

    @Value("${spring.datasource.primary.username:${GEELATO_PRIMARY_JDBCUSER:}}")
    private String username;

    @Value("${spring.datasource.primary.password:${GEELATO_PRIMARY_JDBCPASSWORD:}}")
    private String password;

    @Value("${spring.datasource.primary.driver-class-name:com.mysql.cj.jdbc.Driver}")
    private String driverClassName;

    @Bean(name = "primaryDataSource")
    @Qualifier("primaryDataSource")
    public DataSource primaryDataSource() {
        if (jdbcUrl == null || jdbcUrl.isEmpty()) {
            throw new IllegalStateException("数据库 URL 未配置，请设置 spring.datasource.primary.jdbc-url");
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
