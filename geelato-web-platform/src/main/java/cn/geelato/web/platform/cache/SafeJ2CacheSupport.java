package cn.geelato.web.platform.cache;

import net.oschina.j2cache.CacheChannel;
import net.oschina.j2cache.J2Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SafeJ2CacheSupport {
    private static final Logger log = LoggerFactory.getLogger(SafeJ2CacheSupport.class);

    private static volatile CacheChannel cacheChannel;
    private static volatile boolean initialized;

    private SafeJ2CacheSupport() {
    }

    public static CacheChannel getChannel() {
        CacheChannel local = cacheChannel;
        if (local != null) {
            return local;
        }
        if (initialized) {
            return null;
        }
        synchronized (SafeJ2CacheSupport.class) {
            if (cacheChannel != null) {
                return cacheChannel;
            }
            if (initialized) {
                return null;
            }
            try {
                cacheChannel = J2Cache.getChannel();
            } catch (Exception ex) {
                log.warn("J2Cache is unavailable, fallback to local in-memory cache where supported: {}", ex.getMessage());
            } finally {
                initialized = true;
            }
            return cacheChannel;
        }
    }
}
