package cn.geelato.web.platform.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * j2cache 不可用时的本地降级行为（本模块测试类路径无 j2cache.properties，天然走降级分支）：
 * 降级缓存由 LocalBoundedCache 托管 TTL 与容量，null 值走哨兵防穿透。
 */
class DefaultCacheProviderTest {

    private final DefaultCacheProvider<Object> provider = new DefaultCacheProvider<>();

    @Test
    void putGetRemoveRoundTrip() {
        provider.putCache("platform_app_page_p1", "page");
        assertTrue(provider.exists("platform_app_page_p1"));
        assertEquals("page", provider.getCache("platform_app_page_p1"));

        provider.removeCache("platform_app_page_p1");
        assertFalse(provider.exists("platform_app_page_p1"));
        assertNull(provider.getCache("platform_app_page_p1"));
    }

    @Test
    void nullValueIsCacheableWithoutNpe() {
        provider.putCache("platform_app_page_lang_p1_zh_CN", null);
        assertTrue(provider.exists("platform_app_page_lang_p1_zh_CN"));
        assertNull(provider.getCache("platform_app_page_lang_p1_zh_CN"));
    }

    @Test
    void removeByPatternInLocalFallback() {
        provider.putCache("platform_app_page_p1", "v1");
        provider.putCache("platform_app_page_p2", "v2");
        provider.putCache("platform_app_page_extend_e1", "v3");

        int removed = provider.removeCacheByPattern("platform_app_page_p*");

        assertEquals(2, removed);
        assertFalse(provider.exists("platform_app_page_p1"));
        assertFalse(provider.exists("platform_app_page_p2"));
        assertTrue(provider.exists("platform_app_page_extend_e1"));
    }
}
