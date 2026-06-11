package cn.geelato.web.platform.srv.email.config;

import cn.geelato.datasource.DynamicDataSourceRegistry;
import cn.geelato.datasource.EntityDataSourceResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * 邮件 PostgreSQL 数据源实体映射配置
 * <p>
 * 数据源本身通过 {@code DataSourceConfiguration} 的 {@code @ConfigurationProperties(prefix = "spring.datasource.email-postgre")}
 * 由 Spring Boot 自动创建，并在 {@code DynamicRoutingDataSource.applyTargetDataSources()} 中注册到路由。
 * </p>
 * <p>
 * 本类仅负责将邮件相关实体映射到 {@code email_pg} 数据源，
 * 使 {@code DataSourceInterceptor} 在 Dao 操作这些实体时自动路由到 PostgreSQL。
 * </p>
 */
@Configuration
@ConditionalOnProperty(name = "geelato.email.sync.enabled", havingValue = "true")
@Slf4j
public class EmailDataSourceConfig {

    /**
     * 数据源标识，与 DynamicRoutingDataSource 中的路由 key 一致
     */
    public static final String DATASOURCE_NAME = "secondary";

    private final EntityDataSourceResolver entityDataSourceResolver;

    public EmailDataSourceConfig(EntityDataSourceResolver entityDataSourceResolver) {
        this.entityDataSourceResolver = entityDataSourceResolver;
    }

    @PostConstruct
    public void init() {
        entityDataSourceResolver.addEntityMapping("platform_email_message", DATASOURCE_NAME);
        entityDataSourceResolver.addEntityMapping("platform_email_attachment", DATASOURCE_NAME);
        entityDataSourceResolver.addEntityMapping("platform_email_sync_log", DATASOURCE_NAME);
    }
}
