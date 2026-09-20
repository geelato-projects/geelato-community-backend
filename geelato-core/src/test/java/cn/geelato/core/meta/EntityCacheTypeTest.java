package cn.geelato.core.meta;

import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.TableMeta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EntityCacheType 门控语义:BackEnd/BackEndAndFrontEnd 服务端缓存,None/FrontEnd 不缓存;
 * EntityMeta.getCacheType() 以 TableMeta.cache_type(dev_table 设计器配置)为准。
 */
class EntityCacheTypeTest {

    @Test
    void backEndEnabledMatrix() {
        assertFalse(EntityCacheType.None.backEndEnabled());
        assertTrue(EntityCacheType.BackEnd.backEndEnabled());
        assertFalse(EntityCacheType.FrontEnd.backEndEnabled());
        assertTrue(EntityCacheType.BackEndAndFrontEnd.backEndEnabled());
    }

    @Test
    void fromStringIgnoreCaseParsesDesignerValues() {
        assertTrue(EntityCacheType.fromStringIgnoreCase("backend") == EntityCacheType.BackEnd);
        assertTrue(EntityCacheType.fromStringIgnoreCase("BackEndAndFrontEnd") == EntityCacheType.BackEndAndFrontEnd);
        assertTrue(EntityCacheType.fromStringIgnoreCase(" none ") == EntityCacheType.None);
    }

    @Test
    void entityMetaGateReadsTableMeta() {
        EntityMeta meta = new EntityMeta();
        assertFalse(meta.isBackEndCacheEnabled());
        assertNull(meta.getCacheType());

        TableMeta tableMeta = new TableMeta();
        meta.setTableMeta(tableMeta);
        assertFalse(meta.isBackEndCacheEnabled());

        tableMeta.setCacheType("backend");
        assertTrue(meta.isBackEndCacheEnabled());

        tableMeta.setCacheType("frontend");
        assertFalse(meta.isBackEndCacheEnabled());

        tableMeta.setCacheType("none");
        assertFalse(meta.isBackEndCacheEnabled());
    }
}
