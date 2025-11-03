package cn.geelato.web.common.cache;


public interface CacheProvider<T> {

    void putCache(String key, T value);
    T getCache(String key);
    void removeCache(String key);
    boolean exists(String key);
    
    /**
     * 模糊移除缓存，支持通配符模式
     * @param pattern 匹配模式，支持 *xxx、xxx*、*xxx* 格式
     * @return 移除的缓存数量
     */
    int removeCacheByPattern(String pattern);
}
