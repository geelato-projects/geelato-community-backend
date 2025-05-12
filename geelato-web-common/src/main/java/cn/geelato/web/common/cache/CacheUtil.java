package cn.geelato.web.common.cache;

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

    public static Boolean exists(String key) {
        return cacheService.exists(key);
    }

    public static String generateCacheKeyByGql(String gql) {
        return gql;
    }

}
