package cn.geelato.web.platform.cache;

import net.oschina.j2cache.CacheChannel;
import net.oschina.j2cache.CacheObject;
import net.oschina.j2cache.J2Cache;

public class CacheServiceImpl<T> implements CacheService<T>{
    CacheChannel cache = J2Cache.getChannel();
    @Override
    public void putCache(String key, T value) {
        cache.set("default", key, value);
    }

    @Override
    public T getCache(String key) {
        CacheObject cacheObject= cache.get("default", key);
        if(cacheObject.getValue()!=null){
            return (T)cacheObject.getValue();
        }else {
            return null;
        }
    }

    @Override
    public void removeCache(String key) {

    }
}
