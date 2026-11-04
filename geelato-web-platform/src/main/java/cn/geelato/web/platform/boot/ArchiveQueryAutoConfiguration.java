package cn.geelato.web.platform.boot;

import cn.geelato.datasource.DynamicDataSourceRegistry;
import cn.geelato.web.platform.boot.properties.ArchiveDataSourceProperties;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 归档查询自动装配：
 * <ol>
 *   <li>配置 {@code spring.datasource.archive.jdbc-url} 即构建归档数据源（Hikari，键风格对齐
 *       primary/secondary），并以保留键 {@link #ARCHIVE_DATASOURCE_KEY} 注册进动态数据源注册器——
 *       配置即准入，未配置不装配任何东西，无独立开关；</li>
 *   <li>无条件注册 {@link ArchiveQueryFilter}（order=5，安全1/审计3/实验4 之后）：
 *       未配置归档数据源的宿主收到归档查询 header 时硬失败而非静默忽略。</li>
 * </ol>
 * <p>注意：{@link #ARCHIVE_DATASOURCE_KEY} 为保留键，勿用作 platform_dev_db_connect 行 id，
 * 否则数据源全量刷新时会用表配置覆写此处注册的实例。
 */
@Slf4j
@Configuration
public class ArchiveQueryAutoConfiguration {

    /**
     * 归档数据源在动态数据源体系中的保留键
     */
    public static final String ARCHIVE_DATASOURCE_KEY = "archive";

    @Bean(name = "archiveDataSourceProperties")
    @ConditionalOnProperty(prefix = "spring.datasource.archive", name = "jdbc-url")
    @ConfigurationProperties(prefix = "spring.datasource.archive")
    public ArchiveDataSourceProperties archiveDataSourceProperties() {
        return new ArchiveDataSourceProperties();
    }

    @Bean(name = "archiveDataSource")
    @ConditionalOnMissingBean(name = "archiveDataSource")
    @ConditionalOnProperty(prefix = "spring.datasource.archive", name = "jdbc-url")
    public HikariDataSource archiveDataSource(ArchiveDataSourceProperties properties,
                                              DynamicDataSourceRegistry registry) {
        HikariDataSource ds = buildHikariDataSource(properties);
        registry.registerDataSource(ARCHIVE_DATASOURCE_KEY, ds);
        log.info("归档数据源已注册: {} -> {}", ARCHIVE_DATASOURCE_KEY, properties.getJdbcUrl());
        return ds;
    }

    @Bean
    @ConditionalOnBean(DynamicDataSourceRegistry.class)
    public FilterRegistrationBean<ArchiveQueryFilter> archiveQueryFilterRegistration(DynamicDataSourceRegistry registry) {
        FilterRegistrationBean<ArchiveQueryFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new ArchiveQueryFilter(registry));
        reg.addUrlPatterns("/*");
        reg.setName("archiveQueryFilter");
        // 在安全上下文(1)、审计(3)、实验开关(4)之后执行
        reg.setOrder(5);
        return reg;
    }

    private static HikariDataSource buildHikariDataSource(ArchiveDataSourceProperties props) {
        HikariDataSource ds = new HikariDataSource();
        if (props.getDriverClassName() != null) {
            ds.setDriverClassName(props.getDriverClassName());
        }
        if (props.getJdbcUrl() != null) {
            ds.setJdbcUrl(props.getJdbcUrl());
        }
        if (props.getUsername() != null) {
            ds.setUsername(props.getUsername());
        }
        if (props.getPassword() != null) {
            ds.setPassword(props.getPassword());
        }
        if (props.getMaximumPoolSize() != null) {
            ds.setMaximumPoolSize(props.getMaximumPoolSize());
        }
        if (props.getMinimumIdle() != null) {
            ds.setMinimumIdle(props.getMinimumIdle());
        }
        if (props.getConnectionTimeout() != null) {
            ds.setConnectionTimeout(props.getConnectionTimeout());
        }
        if (props.getIdleTimeout() != null) {
            ds.setIdleTimeout(props.getIdleTimeout());
        }
        if (props.getMaxLifetime() != null) {
            ds.setMaxLifetime(props.getMaxLifetime());
        }
        if (props.getKeepaliveTime() != null) {
            ds.setKeepaliveTime(props.getKeepaliveTime());
        }
        if (props.getValidationTimeout() != null) {
            ds.setValidationTimeout(props.getValidationTimeout());
        }
        if (props.getLeakDetectionThreshold() != null) {
            ds.setLeakDetectionThreshold(props.getLeakDetectionThreshold());
        }
        if (props.getConnectionTestQuery() != null && !props.getConnectionTestQuery().trim().isEmpty()) {
            ds.setConnectionTestQuery(props.getConnectionTestQuery());
        }
        ds.setPoolName(props.getPoolName() == null || props.getPoolName().trim().isEmpty()
                ? "ArchivePool" : props.getPoolName());
        return ds;
    }
}
