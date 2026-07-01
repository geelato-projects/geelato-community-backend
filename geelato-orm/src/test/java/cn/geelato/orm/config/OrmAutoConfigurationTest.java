package cn.geelato.orm.config;

import cn.geelato.core.orm.Dao;
import cn.geelato.orm.executor.DefaultMetaCommandExecutor;
import cn.geelato.orm.executor.MetaCommandExecutor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class OrmAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OrmAutoConfiguration.class));

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
    void shouldFallbackToDynamicDaoForCompatibility() {
        contextRunner.withUserConfiguration(MultiDaoConfiguration.class)
                .run(context -> {
                    MetaCommandExecutor executor = context.getBean(MetaCommandExecutor.class);
                    assertNotNull(executor);
                    Dao dynamicDao = context.getBean("dynamicDao", Dao.class);
                    assertEquals(dynamicDao, extractDao(executor));
                });
    }

    @Test
    void shouldFailWhenMultipleDaoBeansAreAmbiguous() {
        contextRunner.withUserConfiguration(AmbiguousDaoConfiguration.class)
                .run(context -> {
                    assertTrue(context.getStartupFailure() != null);
                    assertTrue(context.getStartupFailure().getMessage().contains("geelato.orm.dao-bean-name"));
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
