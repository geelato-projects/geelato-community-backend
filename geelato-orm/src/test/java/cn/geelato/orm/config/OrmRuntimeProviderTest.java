package cn.geelato.orm.config;

import cn.geelato.core.orm.Dao;
import cn.geelato.orm.executor.DefaultMetaCommandExecutor;
import cn.geelato.orm.executor.MetaCommandExecutor;
import cn.geelato.orm.executor.spi.DaoMetaExecutionStrategy;
import cn.geelato.orm.executor.spi.JdbcTemplateMetaExecutionStrategy;
import cn.geelato.orm.executor.spi.MetaExecutionStrategy;
import cn.geelato.orm.runtime.OrmRuntimeProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;

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
        @SuppressWarnings("unchecked")
        ObjectProvider<MetaExecutionStrategy> strategyProvider = mock(ObjectProvider.class);
        when(applicationContext.getBeanProvider(MetaCommandExecutor.class)).thenReturn(beanProvider);
        when(applicationContext.getBeanProvider(MetaExecutionStrategy.class)).thenReturn(strategyProvider);
        when(beanProvider.getIfAvailable()).thenReturn(null);
        when(strategyProvider.getIfAvailable()).thenReturn(null);
        when(applicationContext.getBean(Dao.class)).thenReturn(primaryDao);

        OrmRuntimeProvider provider = new OrmRuntimeProvider(applicationContext, new OrmProperties());
        MetaCommandExecutor executor = provider.metaCommandExecutor();

        assertNotNull(executor);
        assertInstanceOf(DefaultMetaCommandExecutor.class, executor);
        assertInstanceOf(DaoMetaExecutionStrategy.class, ((DefaultMetaCommandExecutor) executor).getExecutionStrategy());
        assertEquals(primaryDao, ((DaoMetaExecutionStrategy) ((DefaultMetaCommandExecutor) executor).getExecutionStrategy()).getDao());
        assertEquals(executor, provider.metaCommandExecutor());
    }

    @Test
    void shouldCreateJdbcTemplateFallbackExecutorWhenConfigured() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.getDataSource()).thenReturn(mock(DataSource.class));
        @SuppressWarnings("unchecked")
        ObjectProvider<MetaCommandExecutor> executorProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<MetaExecutionStrategy> strategyProvider = mock(ObjectProvider.class);
        when(applicationContext.getBeanProvider(MetaCommandExecutor.class)).thenReturn(executorProvider);
        when(applicationContext.getBeanProvider(MetaExecutionStrategy.class)).thenReturn(strategyProvider);
        when(executorProvider.getIfAvailable()).thenReturn(null);
        when(strategyProvider.getIfAvailable()).thenReturn(null);
        when(applicationContext.containsBean("portalJdbcTemplate")).thenReturn(true);
        when(applicationContext.getBean("portalJdbcTemplate", JdbcTemplate.class)).thenReturn(jdbcTemplate);

        OrmProperties properties = new OrmProperties();
        properties.setExecutionMode(MetaExecutorMode.JDBC_TEMPLATE);
        properties.setJdbcTemplateBeanName("portalJdbcTemplate");

        OrmRuntimeProvider provider = new OrmRuntimeProvider(applicationContext, properties);
        MetaCommandExecutor executor = provider.metaCommandExecutor();

        assertNotNull(executor);
        assertInstanceOf(DefaultMetaCommandExecutor.class, executor);
        assertInstanceOf(JdbcTemplateMetaExecutionStrategy.class, ((DefaultMetaCommandExecutor) executor).getExecutionStrategy());
        assertEquals(jdbcTemplate,
                ((JdbcTemplateMetaExecutionStrategy) ((DefaultMetaCommandExecutor) executor).getExecutionStrategy()).getJdbcTemplate());
    }

    @Configuration
    static class SingleDaoConfiguration {
        @Bean
        Dao primaryDao() {
            return new Dao(mock(JdbcTemplate.class));
        }
    }

}
