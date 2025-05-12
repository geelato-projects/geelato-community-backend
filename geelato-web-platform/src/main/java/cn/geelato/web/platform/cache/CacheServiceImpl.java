package cn.geelato.web.platform.cache;

import net.oschina.j2cache.CacheChannel;
import net.oschina.j2cache.CacheObject;
import net.oschina.j2cache.J2Cache;

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
}
