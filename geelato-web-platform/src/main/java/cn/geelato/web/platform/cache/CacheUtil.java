package cn.geelato.web.platform.cache;

public class CacheUtil {
    private static final CacheService<Object> cacheService=new CacheServiceImpl<>();
    public static Object get(String key) {
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
    public static String generateCacheKeyByGql(String gql){
        return gql;
    }

}
