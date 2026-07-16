package cn.geelato.orm.spi;

import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.util.BeansUtils;
import cn.geelato.orm.query.MetaQuery;
import cn.geelato.orm.spi.support.FluentQueryFilterRuntimeResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FluentQueryFilterRuntimeResolverTest {

    @AfterEach
    void clearContext() {
        new BeansUtils().setApplicationContext(null);
    }

    @Test
    void shouldSkipWhenNoInjectorExists() {
        QueryCommand command = new QueryCommand();

        FluentQueryFilterRuntimeResolver.injectIfAvailable(command, new MetaQuery("demo"));

        assertNull(command.getOriginalWhere());
    }

    @Test
    void shouldUseSingleEnabledInjector() {
        ApplicationContext applicationContext = Mockito.mock(ApplicationContext.class);
        Mockito.when(applicationContext.getBeansOfType(FluentQueryFilterInjector.class))
                .thenReturn(Map.of("injector", new FluentQueryFilterInjector() {
                    @Override
                    public boolean isEnabled() {
                        return true;
                    }

                    @Override
                    public void inject(QueryCommand command, MetaQuery query) {
                        command.setOriginalWhere("enabled");
                    }
                }));
        new BeansUtils().setApplicationContext(applicationContext);

        QueryCommand command = new QueryCommand();
        FluentQueryFilterRuntimeResolver.injectIfAvailable(command, new MetaQuery("demo"));

        assertEquals("enabled", command.getOriginalWhere());
    }

    @Test
    void shouldSkipSingleDisabledInjector() {
        ApplicationContext applicationContext = Mockito.mock(ApplicationContext.class);
        Mockito.when(applicationContext.getBeansOfType(FluentQueryFilterInjector.class))
                .thenReturn(Map.of("injector", new FluentQueryFilterInjector() {
                    @Override
                    public boolean isEnabled() {
                        return false;
                    }

                    @Override
                    public void inject(QueryCommand command, MetaQuery query) {
                        command.setOriginalWhere("should-not-run");
                    }
                }));
        new BeansUtils().setApplicationContext(applicationContext);

        QueryCommand command = new QueryCommand();
        FluentQueryFilterRuntimeResolver.injectIfAvailable(command, new MetaQuery("demo"));

        assertNull(command.getOriginalWhere());
    }

    @Test
    void shouldFailFastWhenMultipleInjectorsExist() {
        ApplicationContext applicationContext = Mockito.mock(ApplicationContext.class);
        Map<String, FluentQueryFilterInjector> injectors = new LinkedHashMap<>();
        injectors.put("injectorA", new NoopFluentInjector());
        injectors.put("injectorB", new NoopFluentInjector());
        Mockito.when(applicationContext.getBeansOfType(FluentQueryFilterInjector.class)).thenReturn(injectors);
        new BeansUtils().setApplicationContext(applicationContext);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> FluentQueryFilterRuntimeResolver.injectIfAvailable(new QueryCommand(), new MetaQuery("demo")));

        assertEquals("Multiple FluentQueryFilterInjector beans found: [injectorA, injectorB]. Expected 0 or 1.", ex.getMessage());
    }

    private static class NoopFluentInjector implements FluentQueryFilterInjector {
        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public void inject(QueryCommand command, MetaQuery query) {
            // no-op
        }
    }
}
