package cn.geelato.web.platform.cache;

import cn.geelato.utils.LocalBoundedCache;
import cn.geelato.web.common.cache.CacheProvider;
import net.oschina.j2cache.CacheChannel;
import net.oschina.j2cache.CacheObject;

import java.util.Collection;

@SuppressWarnings("All")
public class DefaultCacheProvider<T> implements CacheProvider<T> {

    private static final String __Region__ = "default";
    /**
     * j2cache 不可用时的本地降级缓存，TTL/容量对齐 ehcache3.xml default 模板（ttl=1800s, heap=1000）
     */
    private final LocalBoundedCache<String, T> localCache = new LocalBoundedCache<>("j2cache-default-fallback", 1_800_000L, 1_000);

    private CacheChannel cache() {
        return SafeJ2CacheSupport.getChannel();
    }

    @Override
    public void putCache(String key, T value) {
        CacheChannel cache = cache();
        if (cache != null) {
            cache.set(__Region__, key, value);
            return;
        }
        localCache.put(key, value);
    }

    @Override
    public T getCache(String key) {
        CacheChannel cache = cache();
        if (cache != null) {
            CacheObject cacheObject = cache.get(__Region__, key);
            try {
                if (cacheObject.getValue() != null) {
                    return (T) cacheObject.getValue();
                } else {
                    return null;
                }
            } catch (ClassCastException e) {
                return null;
            }
        }
        return localCache.get(key);
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
            // 记录异常但不抛出，返回0表示没有移除任何缓存
            return 0;
        }
    }
    
    /**
     * 检查键是否匹配模式
     * @param key 缓存键
     * @param pattern 匹配模式，支持 *xxx、xxx*、*xxx* 格式
     * @return 是否匹配
     */
    private boolean matchesPattern(String key, String pattern) {
        if (key == null || pattern == null) {
            return false;
        }
        
        // *xxx* 格式：包含指定字符串
        if (pattern.startsWith("*") && pattern.endsWith("*")) {
            String content = pattern.substring(1, pattern.length() - 1);
            return key.contains(content);
        }
        // *xxx 格式：以指定字符串结尾
        else if (pattern.startsWith("*")) {
            String suffix = pattern.substring(1);
            return key.endsWith(suffix);
        }
        // xxx* 格式：以指定字符串开头
        else if (pattern.endsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return key.startsWith(prefix);
        }
        // 精确匹配
        else {
            return key.equals(pattern);
        }
    }
}
