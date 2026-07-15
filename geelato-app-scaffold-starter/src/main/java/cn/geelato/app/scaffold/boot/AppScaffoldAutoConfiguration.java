package cn.geelato.app.scaffold.boot;

import cn.geelato.core.orm.Dao;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import cn.geelato.web.platform.boot.BootApplication;
import cn.geelato.web.platform.boot.MetaConfiguration;

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
    @ConditionalOnProperty(prefix = "geelato.app.scaffold", name = "auto-init-tables", havingValue = "true", matchIfMissing = true)
    public AppScaffoldSchemaInitializer appScaffoldSchemaInitializer(@Qualifier("primaryDao") Dao primaryDao) {
        return new AppScaffoldSchemaInitializer(primaryDao);
    }

    @Bean
    public static BeanFactoryPostProcessor appScaffoldSchemaInitializerDependsOnPostProcessor() {
        return new AppScaffoldSchemaInitializerDependsOnPostProcessor();
    }

    @Bean
    @ConditionalOnBean(name = "primaryJdbcTemplate")
    @ConditionalOnMissingBean(AppScaffoldReadyController.class)
    public AppScaffoldReadyController appScaffoldReadyController(@Qualifier("primaryJdbcTemplate") JdbcTemplate primaryJdbcTemplate,
                                                                 AppScaffoldProperties appScaffoldProperties,
                                                                 Environment environment) {
        return new AppScaffoldReadyController(primaryJdbcTemplate, appScaffoldProperties, environment);
    }

    static class AppScaffoldSchemaInitializerDependsOnPostProcessor implements BeanFactoryPostProcessor {
        private static final String INITIALIZER_BEAN_NAME = "appScaffoldSchemaInitializer";

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            if (!(beanFactory instanceof BeanDefinitionRegistry registry) || !registry.containsBeanDefinition(INITIALIZER_BEAN_NAME)) {
                return;
            }
            addDependsOn(beanFactory, registry, MetaConfiguration.class);
            addDependsOn(beanFactory, registry, BootApplication.class);
        }

        private void addDependsOn(ConfigurableListableBeanFactory beanFactory,
                                  BeanDefinitionRegistry registry,
                                  Class<?> beanType) {
            for (String beanName : beanFactory.getBeanNamesForType(beanType, true, false)) {
                if (!registry.containsBeanDefinition(beanName)) {
                    continue;
                }
                BeanDefinition beanDefinition = registry.getBeanDefinition(beanName);
                String[] existingDependsOn = beanDefinition.getDependsOn();
                if (existingDependsOn == null || existingDependsOn.length == 0) {
                    beanDefinition.setDependsOn(INITIALIZER_BEAN_NAME);
                    continue;
                }
                boolean alreadyDependsOnInitializer = false;
                for (String dependsOn : existingDependsOn) {
                    if (INITIALIZER_BEAN_NAME.equals(dependsOn)) {
                        alreadyDependsOnInitializer = true;
                        break;
                    }
                }
                if (alreadyDependsOnInitializer) {
                    continue;
                }
                String[] mergedDependsOn = new String[existingDependsOn.length + 1];
                System.arraycopy(existingDependsOn, 0, mergedDependsOn, 0, existingDependsOn.length);
                mergedDependsOn[existingDependsOn.length] = INITIALIZER_BEAN_NAME;
                beanDefinition.setDependsOn(mergedDependsOn);
            }
        }
    }
}
