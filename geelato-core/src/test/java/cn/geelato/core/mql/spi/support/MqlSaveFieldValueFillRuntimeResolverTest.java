package cn.geelato.core.mql.spi.support;

import cn.geelato.core.mql.command.CommandType;
import cn.geelato.core.mql.spi.MqlSaveFieldValueFillContext;
import cn.geelato.core.mql.spi.MqlSaveFieldValueFiller;
import cn.geelato.core.util.BeansUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MqlSaveFieldValueFillRuntimeResolverTest {

    @AfterEach
    void clearContext() {
        new BeansUtils().setApplicationContext(null);
    }

    @Test
    void shouldSkipWhenNoFillerExists() {
        Map<String, Object> values = new HashMap<>();

        MqlSaveFieldValueFillRuntimeResolver.fillIfAvailable(
                new MqlSaveFieldValueFillContext("demo", CommandType.Insert, null, Map.of(), values, null)
        );

        assertNull(values.get("creator"));
    }

    @Test
    void shouldUseSingleEnabledFiller() {
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("filler", new MqlSaveFieldValueFiller() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public void fill(MqlSaveFieldValueFillContext context) {
                context.getTargetValueMap().put("creator", "enabled");
            }
        });
        new BeansUtils().setApplicationContext(applicationContext);

        Map<String, Object> values = new HashMap<>();
        MqlSaveFieldValueFillRuntimeResolver.fillIfAvailable(
                new MqlSaveFieldValueFillContext("demo", CommandType.Insert, null, Map.of(), values, null)
        );

        assertEquals("enabled", values.get("creator"));
    }

    @Test
    void shouldSkipSingleDisabledFiller() {
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("filler", new MqlSaveFieldValueFiller() {
            @Override
            public boolean isEnabled() {
                return false;
            }

            @Override
            public void fill(MqlSaveFieldValueFillContext context) {
                context.getTargetValueMap().put("creator", "disabled");
            }
        });
        new BeansUtils().setApplicationContext(applicationContext);

        Map<String, Object> values = new HashMap<>();
        MqlSaveFieldValueFillRuntimeResolver.fillIfAvailable(
                new MqlSaveFieldValueFillContext("demo", CommandType.Insert, null, Map.of(), values, null)
        );

        assertNull(values.get("creator"));
    }

    @Test
    void shouldFailFastWhenMultipleFillersExist() {
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("fillerA", new NoopMqlSaveFieldValueFiller());
        applicationContext.getBeanFactory().registerSingleton("fillerB", new NoopMqlSaveFieldValueFiller());
        new BeansUtils().setApplicationContext(applicationContext);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                MqlSaveFieldValueFillRuntimeResolver.fillIfAvailable(
                        new MqlSaveFieldValueFillContext("demo", CommandType.Insert, null, Map.of(), new HashMap<>(), null))
        );

        assertEquals("Multiple MqlSaveFieldValueFiller beans found: [fillerA, fillerB]. Expected 0 or 1.", ex.getMessage());
    }

    private static class NoopMqlSaveFieldValueFiller implements MqlSaveFieldValueFiller {
        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public void fill(MqlSaveFieldValueFillContext context) {
            // no-op
        }
    }
}
