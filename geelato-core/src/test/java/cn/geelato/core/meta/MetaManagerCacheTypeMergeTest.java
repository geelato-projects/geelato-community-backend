package cn.geelato.core.meta;

import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.IdEntity;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * class 源先注册、DB 源后到的同名冲突("先到先得"半覆盖)下,
 * cache_type 经整体换入的 TableMeta 传播到存活的 class 源实体(与 connect_id 同机制)。
 */
class MetaManagerCacheTypeMergeTest {

    private static final String MERGE = "cache_merge_db_test";
    private static final String DUAL = "CacheMergeClassEntity";
    private static final MetaManager metaManager = MetaManager.singleInstance();

    @AfterAll
    static void tearDown() {
        metaManager.removeOne(MERGE);
        metaManager.removeOne(DUAL);
    }

    @Test
    void halfCoverPropagatesCacheTypeViaTableMetaSwap() {
        metaManager.parseTableEntity(tableMeta(MERGE, "t_cache_merge_db", "none"), columns(), null, null, null);
        EntityMeta first = metaManager.getByEntityName(MERGE);
        assertNotNull(first);
        assertFalse(first.isBackEndCacheEnabled());

        metaManager.parseTableEntity(tableMeta(MERGE, "t_cache_merge_db", "backend"), columns(), null, null, null);
        EntityMeta surviving = metaManager.getByEntityName(MERGE);
        assertSame(first, surviving);
        assertTrue(surviving.isBackEndCacheEnabled());

        // 换回 none 关闭;未配置(null)同样视为关闭
        metaManager.parseTableEntity(tableMeta(MERGE, "t_cache_merge_db", "none"), columns(), null, null, null);
        assertFalse(surviving.isBackEndCacheEnabled());
        metaManager.parseTableEntity(tableMeta(MERGE, "t_cache_merge_db", null), columns(), null, null, null);
        assertFalse(surviving.isBackEndCacheEnabled());
    }

    @Test
    void dualSourceKeepsClassInstanceAndGainsCacheGate() {
        metaManager.parseOne(CacheMergeClassEntity.class);
        EntityMeta classMeta = metaManager.getByEntityName(DUAL);
        assertNotNull(classMeta);
        assertNotNull(classMeta.getClassType());

        metaManager.parseTableEntity(tableMeta(DUAL, "t_cache_merge_class", "backend"), classColumns(), null, null, null);

        EntityMeta surviving = metaManager.getByEntityName(DUAL);
        assertSame(classMeta, surviving);
        assertEquals(CacheMergeClassEntity.class, surviving.getClassType());
        assertTrue(surviving.isBackEndCacheEnabled());
    }

    private static TableMeta tableMeta(String entityName, String tableName, String cacheType) {
        TableMeta tableMeta = new TableMeta();
        tableMeta.setEntityName(entityName);
        tableMeta.setTableName(tableName);
        tableMeta.setCacheType(cacheType);
        return tableMeta;
    }

    private static List<ColumnMeta> columns() {
        ColumnMeta column = new ColumnMeta();
        column.setName("code");
        column.setFieldName("code");
        column.setDataType("varchar");
        return List.of(column);
    }

    private static List<ColumnMeta> classColumns() {
        ColumnMeta column = new ColumnMeta();
        column.setName("name");
        column.setFieldName("name");
        column.setDataType("varchar");
        return List.of(column);
    }

    @Getter
    @Setter
    @Entity(name = "CacheMergeClassEntity", table = "t_cache_merge_class")
    public static class CacheMergeClassEntity extends IdEntity {

        @Col(name = "name", dataType = "VARCHAR", charMaxlength = 128)
        private String name;
    }
}
