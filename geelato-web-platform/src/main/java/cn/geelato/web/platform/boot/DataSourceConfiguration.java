package cn.geelato.web.platform.boot;

import cn.geelato.core.orm.Dao;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;


@SuppressWarnings("ConfigurationProperties")
@Configuration
@Slf4j
public class DataSourceConfiguration extends BaseConfiguration {

    @Bean(name = "primaryDataSource")
    @Qualifier("primaryDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.primary")
    public DataSource primaryDataSource(Environment environment) {
        return buildAndBind(environment, "spring.datasource.primary", "primaryDataSource");
    }
    @Bean(name = "primaryJdbcTemplate")
    public JdbcTemplate primaryJdbcTemplate(@Qualifier("primaryDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
    @Bean(name = "primaryDao")
    public Dao primaryDao(@Qualifier("primaryJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return new Dao(jdbcTemplate);
    }


    // 未配置 spring.datasource.secondary.jdbc-url 时不注册，避免留下一个从未绑定参数的空
    // HikariDataSource，其首次 getConnection() 会抛 "jdbcUrl is required"
    @Bean(name = "secondaryDataSource")
    @Qualifier("secondaryDataSource")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "spring.datasource.secondary", name = "jdbc-url")
    @ConfigurationProperties(prefix = "spring.datasource.secondary")
    public DataSource secondaryDataSource(Environment environment) {
        return buildAndBind(environment, "spring.datasource.secondary", "secondaryDataSource");
    }
    @Bean(name = "secondaryJdbcTemplate")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(name = "secondaryDataSource")
    public JdbcTemplate secondaryJdbcTemplate(@Qualifier("secondaryDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
    @Bean(name = "secondaryDao")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(name = "secondaryJdbcTemplate")
    public Dao secondaryDao(@Qualifier("secondaryJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return new Dao(jdbcTemplate);
    }


    @Bean(name = "dbGenerateDao")
    public Dao DbGenerateDao(@Qualifier("primaryJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return new Dao(jdbcTemplate);
    }

    private DataSource buildAndBind(Environment environment, String prefix, String beanName) {
        DataSource dataSource = DataSourceBuilder.create().build();
        Binder.get(environment).bind(prefix, Bindable.ofInstance(dataSource));
        if (dataSource instanceof HikariDataSource hikariDataSource
                && (hikariDataSource.getJdbcUrl() == null || hikariDataSource.getJdbcUrl().isBlank())) {
            throw new IllegalStateException(String.format(
                    "%s 绑定后仍缺少 jdbc-url：prefix=%s，jdbc-url=%s，username=%s。"
                            + "请检查 application.properties 中 %s.jdbc-url 是否存在、"
                            + "以及相关环境变量（如 CUSTOMER_CLIENT_JDBCURL）是否被设成了空值"
                            + "（占位符 ${VAR:default} 在 VAR 存在但为空时不会回退到默认值）。",
                    beanName, prefix, hikariDataSource.getJdbcUrl(), hikariDataSource.getUsername(), prefix));
        }
        log.info("{} created, prefix={}, jdbc-url={}", beanName, prefix,
                dataSource instanceof HikariDataSource h ? h.getJdbcUrl() : "n/a");
        return dataSource;
    }
}
