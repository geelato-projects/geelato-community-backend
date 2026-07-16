package cn.geelato.core.meta.spi;

import cn.geelato.core.meta.spi.support.EntitySaveFieldValueFillRuntimeResolver;
import cn.geelato.core.mql.command.CommandType;
import cn.geelato.core.util.BeansUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntitySaveFieldValueFillRuntimeResolverTest {

    @AfterEach
    void clearContext() {
        new BeansUtils().setApplicationContext(null);
    }

    @Test
    void shouldSkipWhenNoFillerExists() {
        Map<String, Object> values = new HashMap<>();

        EntitySaveFieldValueFillRuntimeResolver.fillIfAvailable(
                new EntitySaveFieldValueFillContext("demo", CommandType.Insert, null, Map.of(), values, null, null)
        );

        assertNull(values.get("creator"));
    }

    @Test
    void shouldUseSingleEnabledFiller() {
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("filler", new EntitySaveFieldValueFiller() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public void fill(EntitySaveFieldValueFillContext context) {
                context.getTargetValueMap().put("creator", "enabled");
            }
        });
        new BeansUtils().setApplicationContext(applicationContext);

        Map<String, Object> values = new HashMap<>();
        EntitySaveFieldValueFillRuntimeResolver.fillIfAvailable(
                new EntitySaveFieldValueFillContext("demo", CommandType.Insert, null, Map.of(), values, null, null)
        );

        assertEquals("enabled", values.get("creator"));
    }

    @Test
    void shouldSkipSingleDisabledFiller() {
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("filler", new EntitySaveFieldValueFiller() {
            @Override
            public boolean isEnabled() {
                return false;
            }

            @Override
            public void fill(EntitySaveFieldValueFillContext context) {
                context.getTargetValueMap().put("creator", "disabled");
            }
        });
        new BeansUtils().setApplicationContext(applicationContext);

        Map<String, Object> values = new HashMap<>();
        EntitySaveFieldValueFillRuntimeResolver.fillIfAvailable(
                new EntitySaveFieldValueFillContext("demo", CommandType.Insert, null, Map.of(), values, null, null)
        );

        assertNull(values.get("creator"));
    }

    @Test
    void shouldFailFastWhenMultipleFillersExist() {
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("fillerA", new NoopEntitySaveFieldValueFiller());
        applicationContext.getBeanFactory().registerSingleton("fillerB", new NoopEntitySaveFieldValueFiller());
        new BeansUtils().setApplicationContext(applicationContext);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                EntitySaveFieldValueFillRuntimeResolver.fillIfAvailable(
                        new EntitySaveFieldValueFillContext("demo", CommandType.Insert, null, Map.of(), new HashMap<>(), null, null))
        );

        assertEquals("Multiple EntitySaveFieldValueFiller beans found: [fillerA, fillerB]. Expected 0 or 1.", ex.getMessage());
    }

    private static class NoopEntitySaveFieldValueFiller implements EntitySaveFieldValueFiller {
        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public void fill(EntitySaveFieldValueFillContext context) {
            // no-op
        }
    }
}
