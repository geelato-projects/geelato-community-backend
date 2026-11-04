package cn.geelato.datasource;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Id;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataSourceOverrideContextTest {

    @BeforeAll
    static void registerEntities() {
        MetaManager manager = MetaManager.singleInstance();
        manager.parseOne(UnclaimedTestEntity.class);
        manager.parseOne(PlatformCatalogTestEntity.class);
        manager.parseOne(ConnectIdTestEntity.class);
    }

    @AfterAll
    static void unregisterEntities() {
        MetaManager manager = MetaManager.singleInstance();
        manager.removeOne("override_unclaimed_entity");
        manager.removeOne("override_platform_entity");
        manager.removeOne("override_connect_entity");
    }

    @AfterEach
    void cleanUp() {
        DataSourceOverrideContext.clear();
    }

    @Test
    void unclaimedEntityWithoutCatalogOrConnectId() {
        assertTrue(DataSourceOverrideContext.isUnclaimed("override_unclaimed_entity"));
    }

    @Test
    void platformCatalogEntityIsClaimed() {
        assertFalse(DataSourceOverrideContext.isUnclaimed("override_platform_entity"));
    }

    @Test
    void connectIdEntityIsClaimed() {
        assertFalse(DataSourceOverrideContext.isUnclaimed("override_connect_entity"));
    }

    @Test
    void blankOrUnknownEntityIsNotUnclaimed() {
        assertFalse(DataSourceOverrideContext.isUnclaimed(null));
        assertFalse(DataSourceOverrideContext.isUnclaimed(""));
        assertFalse(DataSourceOverrideContext.isUnclaimed("no-such-entity"));
    }

    @Test
    void resolveOverrideKeyReturnsKeyForUnclaimedEntityWhenActive() {
        DataSourceOverrideContext.set("archive", true);
        assertEquals("archive", DataSourceOverrideContext.resolveOverrideKey("override_unclaimed_entity"));
    }

    @Test
    void resolveOverrideKeyReturnsNullWithoutOverride() {
        assertNull(DataSourceOverrideContext.resolveOverrideKey("override_unclaimed_entity"));
    }

    @Test
    void resolveOverrideKeySkipsClaimedEntities() {
        DataSourceOverrideContext.set("archive", true);
        assertNull(DataSourceOverrideContext.resolveOverrideKey("override_platform_entity"));
        assertNull(DataSourceOverrideContext.resolveOverrideKey("override_connect_entity"));
    }

    @Test
    void readOnlyOverrideBlocksWriteOnUnclaimedEntity() {
        DataSourceOverrideContext.set("archive", true);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> DataSourceOverrideContext.assertWritable("save", "override_unclaimed_entity"));
        assertTrue(ex.getMessage().contains("archive"));
        assertTrue(ex.getMessage().contains("override_unclaimed_entity"));
    }

    @Test
    void readOnlyOverrideBlocksWriteWhenEntityUnresolvable() {
        DataSourceOverrideContext.set("archive", true);
        assertThrows(IllegalStateException.class,
                () -> DataSourceOverrideContext.assertWritable("insert", null));
    }

    @Test
    void readOnlyOverrideAllowsWriteOnClaimedEntity() {
        DataSourceOverrideContext.set("archive", true);
        assertDoesNotThrow(() -> DataSourceOverrideContext.assertWritable("save", "override_platform_entity"));
    }

    @Test
    void readOnlyOverrideAllowsReadMethodOnUnclaimedEntity() {
        DataSourceOverrideContext.set("archive", true);
        assertDoesNotThrow(() -> DataSourceOverrideContext.assertWritable("queryForMapList", "override_unclaimed_entity"));
    }

    @Test
    void noOverrideOrWritableOverrideAllowsWrite() {
        assertDoesNotThrow(() -> DataSourceOverrideContext.assertWritable("save", "override_unclaimed_entity"));
        DataSourceOverrideContext.set("archive", false);
        assertDoesNotThrow(() -> DataSourceOverrideContext.assertWritable("save", "override_unclaimed_entity"));
    }

    @Entity(name = "override_unclaimed_entity")
    public static class UnclaimedTestEntity {
        @Id
        private String id;
    }

    @Entity(name = "override_platform_entity", catalog = "platform")
    public static class PlatformCatalogTestEntity {
        @Id
        private String id;
    }

    @Entity(name = "override_connect_entity", connectId = "connect-x")
    public static class ConnectIdTestEntity {
        @Id
        private String id;
    }
}
