package cn.geelato.core.mql.spi.support;

import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.spi.MqlQueryFilterInjector;
import cn.geelato.core.util.BeansUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MqlQueryFilterRuntimeResolverTest {

    @AfterEach
    void clearContext() {
        new BeansUtils().setApplicationContext(null);
    }

    @Test
    void shouldSkipWhenNoInjectorExists() {
        QueryCommand command = new QueryCommand();

        MqlQueryFilterRuntimeResolver.injectIfAvailable(command);

        assertNull(command.getOriginalWhere());
    }

    @Test
    void shouldUseSingleEnabledInjector() {
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("injector", new MqlQueryFilterInjector() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public void inject(QueryCommand command) {
                command.setOriginalWhere("enabled");
            }
        });
        new BeansUtils().setApplicationContext(applicationContext);

        QueryCommand command = new QueryCommand();
        MqlQueryFilterRuntimeResolver.injectIfAvailable(command);

        assertEquals("enabled", command.getOriginalWhere());
    }

    @Test
    void shouldSkipSingleDisabledInjector() {
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("injector", new MqlQueryFilterInjector() {
            @Override
            public boolean isEnabled() {
                return false;
            }

            @Override
            public void inject(QueryCommand command) {
                command.setOriginalWhere("should-not-run");
            }
        });
        new BeansUtils().setApplicationContext(applicationContext);

        QueryCommand command = new QueryCommand();
        MqlQueryFilterRuntimeResolver.injectIfAvailable(command);

        assertNull(command.getOriginalWhere());
    }

    @Test
    void shouldFailFastWhenMultipleInjectorsExist() {
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("injectorA", new NoopMqlInjector());
        applicationContext.getBeanFactory().registerSingleton("injectorB", new NoopMqlInjector());
        new BeansUtils().setApplicationContext(applicationContext);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> MqlQueryFilterRuntimeResolver.injectIfAvailable(new QueryCommand()));

        assertEquals("Multiple MqlQueryFilterInjector beans found: [injectorA, injectorB]. Expected 0 or 1.", ex.getMessage());
    }

    private static class NoopMqlInjector implements MqlQueryFilterInjector {
        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public void inject(QueryCommand command) {
            // no-op
        }
    }
}
