package cn.geelato.core.meta;

import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Id;
import cn.geelato.lang.meta.Title;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 验证实体数据源 connectId 的解析优先级（查询期即时解析，规避扫描期 mapping 未注入的时序问题）：
 * <ol>
 *     <li>{@code @Entity(connectId)} 显式指定 → 直接写入 TableMeta.connectId</li>
 *     <li>{@code @Entity(catalog)} 在 catalogConnectIdMapping 中的映射值 → 运行时 resolveConnectId 回退</li>
 * </ol>
 */
class MetaReflexCatalogMappingTest {

    private final MetaManager metaManager = MetaManager.singleInstance();

    @AfterEach
    void cleanUp() {
        metaManager.setCatalogConnectIdMapping(Collections.emptyMap());
        metaManager.removeOne("catalog_mapped_entity");
        metaManager.removeOne("connect_id_specified_entity");
        metaManager.removeOne("unmapped_entity");
    }

    @Test
    void shouldResolveConnectIdFromCatalogMappingAtRuntime() {
        metaManager.parseOne(CatalogMappedEntity.class);
        // 扫描后再注入映射，验证即时解析（模拟 Spring @PostConstruct 晚于扫描的时序）
        metaManager.setCatalogConnectIdMapping(Collections.singletonMap("biz", "biz_db"));

        assertEquals("biz_db", metaManager.resolveConnectId("catalog_mapped_entity"));
    }

    @Test
    void shouldPreferExplicitConnectIdOverCatalogMapping() {
        metaManager.parseOne(ConnectIdSpecifiedEntity.class);
        metaManager.setCatalogConnectIdMapping(Collections.singletonMap("biz", "biz_db"));

        assertEquals("explicit_db", metaManager.resolveConnectId("connect_id_specified_entity"));
    }

    @Test
    void shouldReturnNullWhenCatalogNotMapped() {
        metaManager.parseOne(UnmappedEntity.class);
        metaManager.setCatalogConnectIdMapping(Collections.emptyMap());

        assertNull(metaManager.resolveConnectId("unmapped_entity"));
    }

    @Title(title = "CatalogMappedEntity")
    @Entity(name = "catalog_mapped_entity", catalog = "biz")
    private static class CatalogMappedEntity {
        @Id
        private String id;
    }

    @Title(title = "ConnectIdSpecifiedEntity")
    @Entity(name = "connect_id_specified_entity", catalog = "biz", connectId = "explicit_db")
    private static class ConnectIdSpecifiedEntity {
        @Id
        private String id;
    }

    @Title(title = "UnmappedEntity")
    @Entity(name = "unmapped_entity", catalog = "unmapped")
    private static class UnmappedEntity {
        @Id
        private String id;
    }
}
