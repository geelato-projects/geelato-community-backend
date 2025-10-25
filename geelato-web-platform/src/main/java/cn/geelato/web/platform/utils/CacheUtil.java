package cn.geelato.web.platform.utils;

import cn.geelato.web.platform.cache.CacheService;
import cn.geelato.web.platform.cache.CacheServiceImpl;

public class CacheUtil {
    private static final CacheService<Object> cacheService = new CacheServiceImpl<>();

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
        return cacheService.getCache(key);
    }

    public static void put(String key, Object value) {
        cacheService.putCache(key, value);
    }

    public static void remove(String key) {
        cacheService.removeCache(key);
    }

    /**
     * 模糊移除缓存，支持通配符模式
     * @param pattern 匹配模式，支持 *xxx、xxx*、*xxx* 格式
     * @return 移除的缓存数量
     */
    public static int removeByPattern(String pattern) {
        return cacheService.removeCacheByPattern(pattern);
    }

    public static Boolean exists(String key) {
        return cacheService.exists(key);
    }

    public static String generateCacheKeyByGql(String gql) {
        return gql;
    }

}
