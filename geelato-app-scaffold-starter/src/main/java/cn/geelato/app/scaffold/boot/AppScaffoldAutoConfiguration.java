package cn.geelato.app.scaffold.boot;

import cn.geelato.core.orm.Dao;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration
@EnableConfigurationProperties(AppScaffoldProperties.class)
public class AppScaffoldAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(OpenAPI.class)
    @ConditionalOnProperty(prefix = "geelato.app.scaffold", name = "openapi-enabled", havingValue = "true", matchIfMissing = true)
    public OpenAPI appScaffoldOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Geelato App Scaffold API")
                .description("OpenAPI document for scaffold applications based on geelato-app-scaffold-starter")
                .version("v1"));
    }

    @Bean
    @ConditionalOnBean(name = "primaryDao")
    @ConditionalOnMissingBean(AppScaffoldSchemaInitializer.class)
    @ConditionalOnProperty(prefix = "geelato.app.scaffold", name = {"enabled", "auto-init-tables"}, havingValue = "true", matchIfMissing = true)
    public AppScaffoldSchemaInitializer appScaffoldSchemaInitializer(@Qualifier("primaryDao") Dao primaryDao) {
        return new AppScaffoldSchemaInitializer(primaryDao);
    }

    @Bean
    @ConditionalOnBean(name = "primaryJdbcTemplate")
    @ConditionalOnMissingBean(AppScaffoldReadyController.class)
    @ConditionalOnProperty(prefix = "geelato.app.scaffold", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AppScaffoldReadyController appScaffoldReadyController(@Qualifier("primaryJdbcTemplate") JdbcTemplate primaryJdbcTemplate,
                                                                 AppScaffoldProperties appScaffoldProperties,
                                                                 Environment environment) {
        return new AppScaffoldReadyController(primaryJdbcTemplate, appScaffoldProperties, environment);
    }
}
