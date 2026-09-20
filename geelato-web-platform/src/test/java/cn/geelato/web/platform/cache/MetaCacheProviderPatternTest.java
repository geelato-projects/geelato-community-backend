package cn.geelato.web.platform.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 失效 pattern 与真实缓存 key 的对应性（本地降级模式，无 j2cache 时走 LocalBoundedCache）：
 * key 形如 mql:{tenant}:{entity}:{md5}:{list|total|map|obj:X|col:X}，
 * "*:{entity}:*" 冒号定界精确命中实体段，不误伤前缀重名实体，跨租户清除。
 * localCache 为 static 共享（对齐 j2cache region 语义），用例间清空隔离。
 */
class MetaCacheProviderPatternTest {

    private final MetaCacheProvider<Object> provider = new MetaCacheProvider<>();

    @BeforeEach
    void resetSharedLocalCache() {
        MetaCacheProvider.clearLocalCacheForTest();
    }

    @Test
    void evictPatternHitsExactEntitySegmentAcrossTenantsAndSuffixes() {
        provider.putCache("mql:geelato:platform_user:AB12CD:list", "v1");
        provider.putCache("mql:tenantB:platform_user:AB12CD:total", 7L);
        provider.putCache("mql:geelato:platform_user:AB12CD:obj:String", "v2");
        provider.putCache("mql:geelato:platform_user_role:EF3456:list", "v3");

        int removed = provider.removeCacheByPattern("*:platform_user:*");

        assertEquals(3, removed);
        assertFalse(provider.exists("mql:geelato:platform_user:AB12CD:list"));
        assertFalse(provider.exists("mql:tenantB:platform_user:AB12CD:total"));
        assertFalse(provider.exists("mql:geelato:platform_user:AB12CD:obj:String"));
        // ":platform_user:" 定界——platform_user_role 不被误伤
        assertTrue(provider.exists("mql:geelato:platform_user_role:EF3456:list"));
    }

    @Test
    void prefixOverlappingEntityNameIsNotOverEvicted() {
        provider.putCache("mql:geelato:platform_user:AB12CD:list", "v1");
        provider.putCache("mql:_:platform_user:AB12CD:list", "v2");

        assertEquals(0, provider.removeCacheByPattern("*:user:*"));
        assertTrue(provider.exists("mql:geelato:platform_user:AB12CD:list"));

        // 命中后可清（get 返回 null），保证"能命中也能清除"
        assertEquals(2, provider.removeCacheByPattern("*:platform_user:*"));
        assertFalse(provider.exists("mql:geelato:platform_user:AB12CD:list"));
        assertFalse(provider.exists("mql:_:platform_user:AB12CD:list"));
    }

    @Test
    void putGetRemoveRoundTrip() {
        String key = "mql:geelato:platform_user:AB12CD:list";
        provider.putCache(key, "value");
        assertTrue(provider.exists(key));
        assertEquals("value", provider.getCache(key));

        provider.removeCache(key);
        assertFalse(provider.exists(key));
    }

    @Test
    void localFallbackIsSharedAcrossProviderInstances() {
        // RuleService 与两个失效监听器各持实例，降级模式必须共享同一份 localCache 才能互通失效
        MetaCacheProvider<Object> writer = new MetaCacheProvider<>();
        MetaCacheProvider<Object> evictor = new MetaCacheProvider<>();

        writer.putCache("mql:geelato:platform_user:AB12CD:list", "v1");

        assertEquals(1, evictor.removeCacheByPattern("*:platform_user:*"));
        assertFalse(writer.exists("mql:geelato:platform_user:AB12CD:list"));
    }

    @Test
    void nullResultIsCacheableWithoutNpe() {
        // dao.queryForMap 空结果返回 null 也会入缓存（对齐 j2cache null-object 防穿透语义）
        String key = "mql:geelato:platform_user:AB12CD:map";
        provider.putCache(key, null);

        assertTrue(provider.exists(key));
        assertNull(provider.getCache(key));
    }
}
