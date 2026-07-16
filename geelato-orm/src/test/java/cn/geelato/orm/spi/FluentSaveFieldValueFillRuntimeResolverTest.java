package cn.geelato.orm.spi;

import cn.geelato.core.util.BeansUtils;
import cn.geelato.orm.spi.support.FluentSaveFieldValueFillRuntimeResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FluentSaveFieldValueFillRuntimeResolverTest {

    @AfterEach
    void clearContext() {
        new BeansUtils().setApplicationContext(null);
    }

    @Test
    void shouldSkipWhenNoFillerExists() {
        Map<String, Object> values = new LinkedHashMap<>();

        FluentSaveFieldValueFillRuntimeResolver.fillIfAvailable(
                new FluentSaveFieldValueFillContext("demo", cn.geelato.core.mql.command.CommandType.Insert, null, Map.of(), values)
        );

        assertNull(values.get("creator"));
    }

    @Test
    void shouldUseSingleEnabledFiller() {
        ApplicationContext applicationContext = Mockito.mock(ApplicationContext.class);
        Mockito.when(applicationContext.getBeansOfType(FluentSaveFieldValueFiller.class))
                .thenReturn(Map.of("filler", new FluentSaveFieldValueFiller() {
                    @Override
                    public boolean isEnabled() {
                        return true;
                    }

                    @Override
                    public void fill(FluentSaveFieldValueFillContext context) {
                        context.getTargetValueMap().put("creator", "enabled");
                    }
                }));
        new BeansUtils().setApplicationContext(applicationContext);

        Map<String, Object> values = new LinkedHashMap<>();
        FluentSaveFieldValueFillRuntimeResolver.fillIfAvailable(
                new FluentSaveFieldValueFillContext("demo", cn.geelato.core.mql.command.CommandType.Insert, null, Map.of(), values)
        );

        assertEquals("enabled", values.get("creator"));
    }

    @Test
    void shouldSkipSingleDisabledFiller() {
        ApplicationContext applicationContext = Mockito.mock(ApplicationContext.class);
        Mockito.when(applicationContext.getBeansOfType(FluentSaveFieldValueFiller.class))
                .thenReturn(Map.of("filler", new FluentSaveFieldValueFiller() {
                    @Override
                    public boolean isEnabled() {
                        return false;
                    }

                    @Override
                    public void fill(FluentSaveFieldValueFillContext context) {
                        context.getTargetValueMap().put("creator", "disabled");
                    }
                }));
        new BeansUtils().setApplicationContext(applicationContext);

        Map<String, Object> values = new LinkedHashMap<>();
        FluentSaveFieldValueFillRuntimeResolver.fillIfAvailable(
                new FluentSaveFieldValueFillContext("demo", cn.geelato.core.mql.command.CommandType.Insert, null, Map.of(), values)
        );

        assertNull(values.get("creator"));
    }

    @Test
    void shouldFailFastWhenMultipleFillersExist() {
        ApplicationContext applicationContext = Mockito.mock(ApplicationContext.class);
        Map<String, FluentSaveFieldValueFiller> fillers = new LinkedHashMap<>();
        fillers.put("fillerA", new NoopFluentSaveFieldValueFiller());
        fillers.put("fillerB", new NoopFluentSaveFieldValueFiller());
        Mockito.when(applicationContext.getBeansOfType(FluentSaveFieldValueFiller.class)).thenReturn(fillers);
        new BeansUtils().setApplicationContext(applicationContext);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                FluentSaveFieldValueFillRuntimeResolver.fillIfAvailable(
                        new FluentSaveFieldValueFillContext("demo", cn.geelato.core.mql.command.CommandType.Insert, null, Map.of(), new LinkedHashMap<>()))
        );

        assertEquals("Multiple FluentSaveFieldValueFiller beans found: [fillerA, fillerB]. Expected 0 or 1.", ex.getMessage());
    }

    private static class NoopFluentSaveFieldValueFiller implements FluentSaveFieldValueFiller {
        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public void fill(FluentSaveFieldValueFillContext context) {
            // no-op
        }
    }
}
