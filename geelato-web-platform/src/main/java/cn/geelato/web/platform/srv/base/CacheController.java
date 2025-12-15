package cn.geelato.web.platform.srv.base;


import cn.geelato.web.common.constants.MediaTypes;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.utils.CacheUtil;
import cn.geelato.web.platform.srv.BaseController;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import net.oschina.j2cache.CacheChannel;
import net.oschina.j2cache.J2Cache;
import net.oschina.j2cache.CacheObject;
import redis.clients.jedis.Jedis;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;


/**
 * @author geelato
 */
@ApiRestController("/cache")
@Slf4j
public class CacheController extends BaseController {

    @Autowired(required = false)
    private Environment environment;
    /**
     * 清除缓存
     * keys: 多个key以英文逗号分隔, 不能为空值
     */
    @RequestMapping(value = {"/remove/{keys}"}, method = {RequestMethod.POST, RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> remove(@PathVariable("keys") String keys) {
        if (Strings.isEmpty(keys)) {
            return ApiResult.fail("Keys is empty");
        }
        for (String key : keys.split(",")) {
            if (StringUtils.isNotBlank(key)) {
                CacheUtil.remove(key);
            }
        }
        return ApiResult.successNoResult();
    }

    @RequestMapping(value = {"/config"}, method = {RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> config() {
        Map<String, Object> result = new HashMap<>();
        boolean channelPresent = false;
        CacheChannel channel = null;
        try {
            channel = J2Cache.getChannel();
            channelPresent = channel != null;
        } catch (Exception ignored) {
        }

        boolean clearRegionSupported = false;
        boolean evictSupported = false;
        if (channel != null) {
            try {
                channel.getClass().getMethod("clear", String.class);
                clearRegionSupported = true;
            } catch (Exception ignored) {
            }
            try {
                channel.getClass().getMethod("evict", String.class, Object.class);
                evictSupported = true;
            } catch (Exception ignored) {
            }
        }

        String l1Provider = p("j2cache.L1.provider_class", "j2cache.l1.provider_class", "j2cache.l1.provider-class");
        String l2Provider = p("j2cache.L2.provider_class", "j2cache.l2.provider_class", "j2cache.l2.provider-class");
        String broadcast = p("j2cache.broadcast", "j2cache.cache.broadcast");
        String serialization = p("j2cache.serialization");
        String cleanOnStart = p("j2cache.clean_on_start", "j2cache.clean-on-start", "j2cache.cleanOnStart");
        String openSpring = p("j2cache.open_spring_cache", "j2cache.open-spring-cache", "j2cache.openSpringCache", "j2cache.cache.open_spring");
        String defaultExpire = p("j2cache.default_cache_expire", "j2cache.default-cache-expire");

        Map<String, Object> l1 = new HashMap<>();
        l1.put("enabled", StringUtils.isNotBlank(l1Provider) && !"none".equalsIgnoreCase(l1Provider));
        l1.put("provider", l1Provider);

        Map<String, Object> l2 = new HashMap<>();
        l2.put("enabled", StringUtils.isNotBlank(l2Provider) && !"none".equalsIgnoreCase(l2Provider));
        l2.put("provider", l2Provider);

        Map<String, Object> l2cfg = new HashMap<>();
        l2cfg.put("mode", p("redis.mode"));
        l2cfg.put("hosts", p("redis.hosts"));
        l2cfg.put("namespace", p("redis.namespace"));
        l2cfg.put("database", p("redis.database"));
        if (Strings.isNotEmpty(p("redis.password"))) {
            l2cfg.put("password", "******");
        }
        l2.put("config", l2cfg);

        Map<String, Object> cfg = new HashMap<>();
        cfg.put("open_spring_cache", openSpring);
        cfg.put("clean_on_start", cleanOnStart);
        cfg.put("broadcast", broadcast);
        cfg.put("serialization", serialization);
        cfg.put("default_cache_expire", defaultExpire);

        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("channel_present", channelPresent);
        capabilities.put("clear_region_supported", clearRegionSupported);
        capabilities.put("evict_supported", evictSupported);

        result.put("l1", l1);
        result.put("l2", l2);
        result.put("config", cfg);
        result.put("capabilities", capabilities);

        return ApiResult.success(result);
    }


    private Properties j2Props;

    private String p(String... keys) {
        if (environment == null) return null;
        for (String k : keys) {
            try {
                String v = environment.getProperty(k);
                if (Strings.isNotEmpty(v)) return v;
            } catch (Exception ignored) {
            }
        }
        if (j2Props == null) {
            j2Props = loadJ2Props();
        }
        if (j2Props != null) {
            for (String k : keys) {
                String v = j2Props.getProperty(k);
                if (Strings.isNotEmpty(v)) return v;
            }
        }
        return null;
    }

    private Properties loadJ2Props() {
        String[] candidates = new String[]{"j2cache.properties", "j2cache/j2cache.properties"};
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        for (String name : candidates) {
            try (InputStream is = (cl != null ? cl.getResourceAsStream(name) : CacheController.class.getClassLoader().getResourceAsStream(name))) {
                if (is != null) {
                    Properties props = new Properties();
                    props.load(is);
                    return props;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }


    @RequestMapping(value = {"/list/l1"}, method = {RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> listL1() {
        List<String> l2Keys = getL2Keys();
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            CacheChannel channel = J2Cache.getChannel();
            for (String k : l2Keys) {
                if (k == null) continue;
                CacheObject obj = channel.get("default", k);
                if (obj != null && obj.getValue() != null && obj.getLevel() == 1) {
                    list.add(buildInfo(k, obj, "L1"));
                }
            }
        } catch (Exception ex) {
            log.error("list l1 keys error", ex);
        }
        return ApiResult.success(list);
    }

    @RequestMapping(value = {"/list/l2"}, method = {RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> listL2() {
        List<String> l2Keys = getL2Keys();
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            CacheChannel channel = J2Cache.getChannel();
            for (String k : l2Keys) {
                CacheObject obj = channel.get("default", k);
                list.add(buildInfo(k, obj, "L2"));
            }
        } catch (Exception ex) {
            log.error("list l2 keys error", ex);
        }
        return ApiResult.success(list);
    }

    @RequestMapping(value = {"/list/lx/{likeKey}"}, method = {RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> listLikeAll(@PathVariable("likeKey") String likeKey) {
        if (Strings.isEmpty(likeKey)) {
            return ApiResult.success(List.of());
        }
        List<Map<String, Object>> result = new ArrayList<>();
        List<String> l2Keys = getL2Keys();
        try {
            CacheChannel channel = J2Cache.getChannel();
            for (String k : l2Keys) {
                if (k != null && k.contains(likeKey)) {
                    CacheObject obj = channel.get("default", k);
                    String type = "L2";
                    if (obj != null && obj.getValue() != null && obj.getLevel() == 1) {
                        type = "L1";
                    }
                    Map<String, Object> info = buildInfo(k, obj, type);
                    if (!containsKey(result, k)) {
                        result.add(info);
                    }
                }
            }
        } catch (Exception ex) {
            log.error("list all like keys error", ex);
        }
        return ApiResult.success(result);
    }

    @RequestMapping(value = {"/get/{key}"}, method = {RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> get(@PathVariable("key") String key) {
        if (Strings.isEmpty(key)) {
            return ApiResult.fail("Key is empty");
        }
        Object value = getCacheValue(key);
        return ApiResult.success(value);
    }

    @RequestMapping(value = {"/clear"}, method = {RequestMethod.POST, RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> clearAll() {
        clearAllCaches();
        return ApiResult.successNoResult();
    }


    private List<String> getAllCacheKeys() {
        try {
            CacheChannel channel = J2Cache.getChannel();
            Collection<String> keys = channel.keys("default");
            List<String> list = new ArrayList<>();
            if (keys != null) {
                for (Object o : keys) {
                    if (o != null) {
                        list.add(String.valueOf(o));
                    }
                }
            }
            return list;
        } catch (Exception ex) {
            log.error("list cache keys error", ex);
        }
        return new ArrayList<>();
    }

    private List<String> getL2Keys() {
        try {
            CacheChannel channel = J2Cache.getChannel();
            Collection<String> keys = channel.keys("default");
            List<String> list = new ArrayList<>();
            if (keys != null) {
                for (Object o : keys) {
                    if (o != null) {
                        list.add(String.valueOf(o));
                    }
                }
            }
            return list;
        } catch (Exception ex) {
            log.error("list l2 keys error", ex);
        }
        return new ArrayList<>();
    }

    private Map<String, Object> buildInfo(String key, CacheObject obj, String type) {
        Map<String, Object> info = new HashMap<>();
        info.put("key", key);
        info.put("type", type);
        Object value = obj == null ? null : obj.getValue();
        info.put("size", sizeOf(value));
        Long expireAt = null;
        if ("L2".equalsIgnoreCase(type)) {
            Long ttl = getRedisTTL("default", key);
            if (ttl != null && ttl >= 0) {
                expireAt = Instant.now().toEpochMilli() + ttl * 1000;
            }
        }
        info.put("createTime", null);
        info.put("expireTime", expireAt);
        return info;
    }

    private boolean containsKey(List<Map<String, Object>> list, String key) {
        for (Map<String, Object> m : list) {
            Object k = m.get("key");
            if (k != null && String.valueOf(k).equals(key)) return true;
        }
        return false;
    }

    private long sizeOf(Object v) {
        if (v == null) return 0L;
        if (v instanceof byte[]) return ((byte[]) v).length;
        if (v instanceof CharSequence) return ((CharSequence) v).toString().getBytes(StandardCharsets.UTF_8).length;
        return String.valueOf(v).getBytes(StandardCharsets.UTF_8).length;
    }

    private Long getRedisTTL(String region, String key) {
        try {
            String mode = p("redis.mode");
            if (mode == null || !"single".equalsIgnoreCase(mode)) return null;
            String hosts = p("redis.hosts");
            if (hosts == null || hosts.isEmpty()) return null;
            String[] hp = hosts.split(",")[0].split(":");
            String host = hp[0];
            int port = hp.length > 1 ? Integer.parseInt(hp[1]) : 6379;
            String password = p("redis.password");
            String dbStr = p("redis.database");
            int db = 0;
            try { if (dbStr != null && !dbStr.isEmpty()) db = Integer.parseInt(dbStr); } catch (Exception ignored) {}
            String namespace = p("redis.namespace");
            String nsKey = (namespace != null && !namespace.isEmpty() ? namespace + ":" : "") + region + ":" + key;
            Jedis jedis = new Jedis(host, port);
            if (password != null && !password.isEmpty()) jedis.auth(password);
            if (db > 0) jedis.select(db);
            Long ttl = jedis.ttl(nsKey);
            jedis.close();
            return ttl;
        } catch (Exception e) {
            return null;
        }
    }

    private Object getCacheValue(String key) {
        try {
            Method m = null;
            String[] candidates = new String[]{"get", "getValue"};
            for (String name : candidates) {
                try {
                    m = CacheUtil.class.getMethod(name, String.class);
                    break;
                } catch (NoSuchMethodException ignored) {
                }
            }
            if (m != null) {
                return m.invoke(null, key);
            }
        } catch (Exception ex) {
            log.error("get cache value error", ex);
        }
        return null;
    }

    private void clearAllCaches() {
        try {
            CacheChannel channel = J2Cache.getChannel();
            channel.clear("default");
            return;
        } catch (Exception ex) {
            log.error("clear all cache error", ex);
        }
        List<String> keys = getAllCacheKeys();
        for (String k : keys) {
            if (StringUtils.isNotBlank(k)) {
                CacheUtil.remove(k);
            }
        }
    }

}
