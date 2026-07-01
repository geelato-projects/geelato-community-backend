package cn.geelato.web.platform.utils;

import cn.geelato.web.common.cache.CacheProvider;
import cn.geelato.web.platform.cache.DefaultCacheProvider;

public class CacheUtil {
    private static final CacheProvider<Object> cacheProvider = new DefaultCacheProvider<>();


    /**
     * 获取缓存
     *
     * @param key 缓存的key
     * @return 获取不到时，返回null
     */
    public static Object get(String key) {
        if (key == null||!CacheUtil.exists(key)) {
            return null;
        }
        return cacheProvider.getCache(key);
    }

    public static void put(String key, Object value) {
        cacheProvider.putCache(key, value);
    }

    public static void remove(String key) {
        cacheProvider.removeCache(key);
    }

    /**
     * 模糊移除缓存，支持通配符模式
     * @param pattern 匹配模式，支持 *xxx、xxx*、*xxx* 格式
     * @return 移除的缓存数量
     */
    public static int removeByPattern(String pattern) {
        return cacheProvider.removeCacheByPattern(pattern);
    }

    public static Boolean exists(String key) {
        return cacheProvider.exists(key);
    }

}
