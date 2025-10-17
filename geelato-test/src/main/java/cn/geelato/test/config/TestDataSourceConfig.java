package cn.geelato.test.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 测试数据源配置
 */
@Slf4j
@Configuration
public class TestDataSourceConfig {


    /**
     * 测试数据源
     * 只有在配置了spring.datasource.test时才会创建
     */
    @Bean(name = "testDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.test")
    @ConditionalOnProperty(prefix = "spring.datasource.test", name = "jdbc-url")
    public DataSource testDataSource() {
        log.info("初始化测试数据源");
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    /**
     * 默认测试数据源
     * 当没有配置spring.datasource.test时使用
     */
    @Bean(name = "testDataSource")
    @ConditionalOnProperty(prefix = "spring.datasource.test", name = "jdbc-url", matchIfMissing = true)
    public DataSource defaultTestDataSource(@Qualifier("primaryDataSource") DataSource primaryDataSource) {
        log.info("未配置测试数据源，使用主数据源作为测试数据源");
        return primaryDataSource;
    }
}