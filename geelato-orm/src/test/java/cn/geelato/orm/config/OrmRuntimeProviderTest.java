package cn.geelato.orm.config;

import cn.geelato.core.orm.Dao;
import cn.geelato.orm.executor.DefaultMetaCommandExecutor;
import cn.geelato.orm.executor.MetaCommandExecutor;
import cn.geelato.orm.runtime.OrmRuntimeProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrmRuntimeProviderTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OrmAutoConfiguration.class));

    @Test
    void shouldReuseSpringMetaCommandExecutorBean() {
        contextRunner.withUserConfiguration(SingleDaoConfiguration.class)
                .run(context -> {
                    OrmRuntimeProvider provider = context.getBean(OrmRuntimeProvider.class);
                    MetaCommandExecutor executor = context.getBean(MetaCommandExecutor.class);
                    assertEquals(executor, provider.metaCommandExecutor());
                });
    }

    @Test
    void shouldCreateFallbackExecutorWhenBeanIsNotRegistered() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Dao primaryDao = new Dao(jdbcTemplate);
        @SuppressWarnings("unchecked")
        ObjectProvider<MetaCommandExecutor> beanProvider = mock(ObjectProvider.class);
        when(applicationContext.getBeanProvider(MetaCommandExecutor.class)).thenReturn(beanProvider);
        when(beanProvider.getIfAvailable()).thenReturn(null);
        when(applicationContext.getBean(Dao.class)).thenReturn(primaryDao);

        OrmRuntimeProvider provider = new OrmRuntimeProvider(applicationContext, new OrmProperties());
        MetaCommandExecutor executor = provider.metaCommandExecutor();

        assertNotNull(executor);
        assertInstanceOf(DefaultMetaCommandExecutor.class, executor);
        assertEquals(primaryDao, extractDao(executor));
        assertEquals(executor, provider.metaCommandExecutor());
    }

    @Configuration
    static class SingleDaoConfiguration {
        @Bean
        Dao primaryDao() {
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
