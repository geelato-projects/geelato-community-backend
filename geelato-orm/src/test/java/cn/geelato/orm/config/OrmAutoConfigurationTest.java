package cn.geelato.orm.config;

import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.util.BeansUtils;
import cn.geelato.orm.executor.DefaultMetaCommandExecutor;
import cn.geelato.orm.executor.MetaCommandExecutor;
import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class OrmAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OrmAutoConfiguration.class));
    private final ApplicationContextRunner scannedConfigurationRunner = new ApplicationContextRunner()
            .withUserConfiguration(OrmAutoConfiguration.class);

    @AfterEach
    void clearDefaultDataSourceKey() {
        DataSourceManager.singleInstance().setDefaultDataSourceKey(null);
    }

    @Test
    void shouldUseSingleDaoBean() {
        contextRunner.withUserConfiguration(SingleDaoConfiguration.class)
                .run(context -> {
                    MetaCommandExecutor executor = context.getBean(MetaCommandExecutor.class);
                    assertNotNull(executor);
                    assertInstanceOf(DefaultMetaCommandExecutor.class, executor);
                });
    }

    @Test
    void shouldExposeMetaCommandExecutorToBeansUtils() {
        contextRunner.withUserConfiguration(SingleDaoConfiguration.class)
                .run(context -> {
                    MetaCommandExecutor executor = context.getBean(MetaCommandExecutor.class);
                    assertEquals(executor, BeansUtils.getBean(MetaCommandExecutor.class));
                });
    }

    @Test
    void shouldUseConfiguredDaoBeanName() {
        contextRunner
                .withPropertyValues("geelato.orm.dao-bean-name=dynamicDao")
                .withUserConfiguration(MultiDaoConfiguration.class)
                .run(context -> {
                    MetaCommandExecutor executor = context.getBean(MetaCommandExecutor.class);
                    assertNotNull(executor);
                    assertInstanceOf(DefaultMetaCommandExecutor.class, executor);
                });
    }

    @Test
    void shouldFallbackToPrimaryDaoForCompatibility() {
        contextRunner.withUserConfiguration(MultiDaoConfiguration.class)
                .run(context -> {
                    MetaCommandExecutor executor = context.getBean(MetaCommandExecutor.class);
                    assertNotNull(executor);
                    Dao primaryDao = context.getBean("primaryDao", Dao.class);
                    assertEquals(primaryDao, extractDao(executor));
                });
    }

    @Test
    void shouldDefaultDataSourceKeyToPrimaryWhenPrimaryInfrastructureExists() {
        contextRunner.withUserConfiguration(PrimaryInfrastructureConfiguration.class)
                .run(context -> assertEquals("primary", DataSourceManager.singleInstance().getDefaultDataSourceKey()));
    }

    @Test
    void shouldFailWhenMultipleDaoBeansAreAmbiguous() {
        contextRunner.withUserConfiguration(AmbiguousDaoConfiguration.class)
                .run(context -> {
                    assertTrue(context.getStartupFailure() != null);
                    assertTrue(context.getStartupFailure().getMessage().contains("geelato.orm.dao-bean-name"));
                });
    }

    @Test
    void shouldCreateMetaCommandExecutorWhenOrmConfigurationIsRegisteredAsRegularConfiguration() {
        scannedConfigurationRunner.withUserConfiguration(SingleDaoConfiguration.class)
                .run(context -> {
                    MetaCommandExecutor executor = context.getBean(MetaCommandExecutor.class);
                    assertNotNull(executor);
                    assertInstanceOf(DefaultMetaCommandExecutor.class, executor);
                });
    }

    @Test
    void shouldFailFastWhenDaoBeanIsMissing() {
        contextRunner.run(context -> {
            assertTrue(context.getStartupFailure() != null);
            assertTrue(context.getStartupFailure().getMessage().contains("No Dao bean found for MetaCommandExecutor"));
        });
    }

    @Configuration
    static class SingleDaoConfiguration {
        @Bean
        Dao primaryDao() {
            return new Dao(mock(JdbcTemplate.class));
        }
    }

    @Configuration
    static class MultiDaoConfiguration {
        @Bean
        Dao primaryDao() {
            return new Dao(mock(JdbcTemplate.class));
        }

        @Bean
        Dao dynamicDao() {
            return new Dao(mock(JdbcTemplate.class));
        }
    }

    @Configuration
    static class PrimaryInfrastructureConfiguration {
        @Bean
        DataSource primaryDataSource() {
            return mock(DataSource.class);
        }

        @Bean
        JdbcTemplate primaryJdbcTemplate() {
            JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
            org.mockito.Mockito.when(jdbcTemplate.getDataSource()).thenReturn(primaryDataSource());
            return jdbcTemplate;
        }

        @Bean
        Dao primaryDao() {
            return new Dao(primaryJdbcTemplate());
        }
    }

    @Configuration
    static class AmbiguousDaoConfiguration {
        @Bean
        Dao secondaryDao() {
            return new Dao(mock(JdbcTemplate.class));
        }

        @Bean
        Dao dbGenerateDao() {
            return new Dao(mock(JdbcTemplate.class));
        }
    }

    private Dao extractDao(MetaCommandExecutor executor) {
        try {
            Field field = DefaultMetaCommandExecutor.class.getDeclaredField("dao");
            field.setAccessible(true);
            return (Dao) field.get(executor);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to inspect MetaCommandExecutor dao", e);
        }
    }
}
