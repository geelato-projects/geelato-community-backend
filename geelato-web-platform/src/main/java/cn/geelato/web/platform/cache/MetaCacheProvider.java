package cn.geelato.web.platform.cache;

import cn.geelato.utils.LocalBoundedCache;
import cn.geelato.web.common.cache.CacheProvider;
import net.oschina.j2cache.CacheChannel;
import net.oschina.j2cache.CacheObject;

import java.util.Collection;

@SuppressWarnings("All")
public class MetaCacheProvider<T> implements CacheProvider<T> {
    private static final String __Region__ = "metaquery";
    /**
     * j2cache 不可用时的本地降级缓存，TTL/容量对齐 ehcache3.xml metaQueryTemplate（ttl=60s, heap=2000）。
     * 必须 static：RuleService 与两个失效监听器各持一个 Provider 实例，降级模式下只有共享同一份
     * localCache，removeCacheByPattern 的失效才能互通（对齐 j2cache 同 region 的语义）。
     */
    private static final LocalBoundedCache<String, Object> localCache = new LocalBoundedCache<>("j2cache-metaquery-fallback", 60_000L, 2_000);
    private static CacheValueAdapter adapter = new DefaultCacheValueAdapter();

    private CacheChannel cache() {
        return SafeJ2CacheSupport.getChannel();
    }

    /** 仅测试用：localCache 为 static 共享，用例间需显式清空隔离 */
    static void clearLocalCacheForTest() {
        localCache.clear();
    }

    public static void setAdapter(CacheValueAdapter newAdapter) {
        if (newAdapter != null) {
            adapter = newAdapter;
        }
    }

    @Override
    public void putCache(String key, T value) {
        Object adapted = adapter.adaptForStore(value);
        CacheChannel cache = cache();
        if (cache != null) {
            cache.set(__Region__, key, adapted);
            return;
        }
        localCache.put(key, adapted);
    }

    @Override
    public T getCache(String key) {
        CacheChannel cache = cache();
        if (cache != null) {
            CacheObject cacheObject = cache.get(__Region__, key);
            try {
                if (cacheObject.getValue() != null) {
                    return (T) adapter.adaptForLoad(cacheObject.getValue());
                } else {
                    return null;
                }
            } catch (ClassCastException e) {
                return null;
            }
        }
        Object localValue = localCache.get(key);
        return localValue == null ? null : (T) adapter.adaptForLoad(localValue);
    }

    @Override
    public void removeCache(String key) {
        CacheChannel cache = cache();
        if (cache != null) {
            cache.evict(__Region__, key);
            return;
        }
        localCache.remove(key);
    }

    @Override
    public boolean exists(String key) {
        CacheChannel cache = cache();
        if (cache != null) {
            return cache.exists(__Region__, key);
        }
        return localCache.exists(key);
    }

    @Override
    public int removeCacheByPattern(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return 0;
        }
        try {
            CacheChannel cache = cache();
            Collection<String> keys = cache != null ? cache.keys(__Region__) : localCache.liveKeys();
            if (keys == null || keys.isEmpty()) {
                return 0;
            }
            int removedCount = 0;
            String trimmedPattern = pattern.trim();
            for (String key : keys) {
                if (matchesPattern(key, trimmedPattern)) {
                    if (cache != null) {
                        cache.evict(__Region__, key);
                    } else {
                        localCache.remove(key);
                    }
                    removedCount++;
                }
            }
            return removedCount;
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean matchesPattern(String key, String pattern) {
        if (key == null || pattern == null) {
            return false;
        }
        if (pattern.startsWith("*") && pattern.endsWith("*")) {
            String content = pattern.substring(1, pattern.length() - 1);
            return key.contains(content);
        } else if (pattern.startsWith("*")) {
            String suffix = pattern.substring(1);
            return key.endsWith(suffix);
        } else if (pattern.endsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return key.startsWith(prefix);
        } else {
            return key.equals(pattern);
        }
    }
}
