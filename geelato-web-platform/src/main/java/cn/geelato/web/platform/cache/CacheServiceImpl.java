package cn.geelato.web.platform.cache;

import net.oschina.j2cache.CacheChannel;
import net.oschina.j2cache.CacheObject;
import net.oschina.j2cache.J2Cache;
import java.util.Collection;

@SuppressWarnings("All")
public class CacheServiceImpl<T> implements CacheService<T>{

    private final String __Region__="default";
    CacheChannel cache = J2Cache.getChannel();
    @Override
    public void putCache(String key, T value) {
        cache.set(__Region__, key, value);
    }

    @Override
    public T getCache(String key) {
        CacheObject cacheObject= cache.get(__Region__, key);
        try{
            if(cacheObject.getValue()!=null){
                return (T)cacheObject.getValue();
            }else {
                return null;
            }
        }catch (ClassCastException e){
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
            // 获取所有缓存键
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
