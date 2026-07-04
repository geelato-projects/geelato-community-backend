package cn.geelato.orm.config;

import cn.geelato.datasource.DynamicDataSourceRegistry;
import cn.geelato.datasource.DynamicRoutingDataSource;
import cn.geelato.datasource.spi.DynamicDataSourceDefinitionLoader;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class OrmDynamicDataSourceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    OrmAutoConfiguration.class,
                    OrmDynamicDataSourceAutoConfiguration.class
            ))
            .withUserConfiguration(PrimaryJdbcTemplateConfiguration.class);

    @Test
    void shouldAutoConfigureDynamicDataSourceBeansWhenPrimaryJdbcTemplatePresent() {
        contextRunner.run(context -> {
            assertNotNull(context.getBean("dynamicDataSource", DynamicRoutingDataSource.class));
            assertNotNull(context.getBean("dynamicJdbcTemplate", JdbcTemplate.class));
            assertNotNull(context.getBean("dynamicDao"));
            assertNotNull(context.getBean(DynamicDataSourceRegistry.class));
        });
    }

    @Configuration
    static class PrimaryJdbcTemplateConfiguration {
        @Bean(name = "primaryJdbcTemplate")
        JdbcTemplate primaryJdbcTemplate() {
            DataSource dataSource = mock(DataSource.class);
            return new JdbcTemplate(dataSource);
        }

        @Bean
        DynamicDataSourceDefinitionLoader dynamicDataSourceDefinitionLoader() {
            return new DynamicDataSourceDefinitionLoader() {
                @Override
                public java.util.List<java.util.Map<String, Object>> loadAll() {
                    return java.util.Collections.emptyList();
                }

                @Override
                public java.util.Map<String, Object> loadOne(String key) {
                    return null;
                }
            };
        }
    }
}

