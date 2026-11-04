package cn.geelato.datasource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DynamicDataSourceScopeTest {

    @AfterEach
    void cleanUp() {
        DynamicDataSourceHolder.clearDataSourceKey();
    }

    @Test
    void withDataSourceSetsKeyInsideScopeAndRestoresAfter() {
        DynamicDataSourceScope.switchTo("outer-key");
        String result = DynamicDataSourceScope.withDataSource("scoped-key", () -> {
            assertEquals("scoped-key", DynamicDataSourceHolder.getDataSourceKey());
            return "ok";
        });
        assertEquals("ok", result);
        assertEquals("outer-key", DynamicDataSourceHolder.getDataSourceKey());
    }

    @Test
    void withDataSourceClearsWhenNoPreviousKey() {
        DynamicDataSourceScope.withDataSource("scoped-key", () -> null);
        assertNull(DynamicDataSourceHolder.getDataSourceKey());
    }

    @Test
    void withDataSourceRestoresPreviousEvenOnException() {
        DynamicDataSourceScope.switchTo("outer-key");
        assertThrows(IllegalStateException.class,
                () -> DynamicDataSourceScope.withDataSource("scoped-key", () -> {
                    throw new IllegalStateException("boom");
                }));
        assertEquals("outer-key", DynamicDataSourceHolder.getDataSourceKey());
    }

    @Test
    void withDataSourceBlankConnectIdLeavesCurrentKeyUntouched() {
        DynamicDataSourceScope.switchTo("outer-key");
        AtomicReference<String> seen = new AtomicReference<>();
        DynamicDataSourceScope.withDataSource(null, () -> {
            seen.set(DynamicDataSourceHolder.getDataSourceKey());
            return null;
        });
        assertEquals("outer-key", seen.get());
        assertEquals("outer-key", DynamicDataSourceHolder.getDataSourceKey());
    }

    @Test
    void switchToRejectsBlankConnectId() {
        assertThrows(IllegalArgumentException.class, () -> DynamicDataSourceScope.switchTo(" "));
        assertNull(DynamicDataSourceHolder.getDataSourceKey());
    }
}
