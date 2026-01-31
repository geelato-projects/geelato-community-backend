package cn.geelato.web.platform.cache;

import cn.geelato.web.common.cache.CacheProvider;
import net.oschina.j2cache.CacheChannel;
import net.oschina.j2cache.CacheObject;
import net.oschina.j2cache.J2Cache;

import java.util.Collection;

@SuppressWarnings("All")
public class MetaCacheProvider<T> implements CacheProvider<T> {
    private final String __Region__ = "metaquery";
    private final CacheChannel cache = J2Cache.getChannel();
    private static CacheValueAdapter adapter = new DefaultCacheValueAdapter();

    public static void setAdapter(CacheValueAdapter newAdapter) {
        if (newAdapter != null) {
            adapter = newAdapter;
        }
    }

    @Override
    public void putCache(String key, T value) {
        cache.set(__Region__, key, (T) adapter.adaptForStore(value));
    }

    @Override
    public T getCache(String key) {
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

    @Override
    public void removeCache(String key) {
        cache.evict(__Region__, key);
    }

    @Override
    public boolean exists(String key) {
        return cache.exists(__Region__, key);
    }

    @Override
    public int removeCacheByPattern(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return 0;
        }
        try {
            Collection<String> keys = cache.keys(__Region__);
            if (keys == null || keys.isEmpty()) {
                return 0;
            }
            int removedCount = 0;
            String trimmedPattern = pattern.trim();
            for (String key : keys) {
                if (matchesPattern(key, trimmedPattern)) {
                    cache.evict(__Region__, key);
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
