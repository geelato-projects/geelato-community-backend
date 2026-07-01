package cn.geelato.web.platform.cache;

public interface CacheValueAdapter {
    Object adaptForStore(Object value);
    Object adaptForLoad(Object value);
}
