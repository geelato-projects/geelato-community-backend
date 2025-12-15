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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


/**
 * @author geelato
 */
@ApiRestController("/cache")
@Slf4j
public class CacheController extends BaseController {

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

    @RequestMapping(value = {"/list", "/list/{likeKey}"}, method = {RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> list(@PathVariable(value = "likeKey", required = false) String likeKey) {
        List<String> keys = getAllCacheKeys();
        if (StringUtils.isNotBlank(likeKey)) {
            List<String> filtered = new ArrayList<>();
            for (String k : keys) {
                if (k != null && k.contains(likeKey)) {
                    filtered.add(k);
                }
            }
            keys = filtered;
        }
        return ApiResult.success(keys);
    }

    @RequestMapping(value = {"/get/{key}"}, method = {RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> get(@PathVariable("key") String key) {
        if (Strings.isEmpty(key)) {
            return ApiResult.fail("Key is empty");
        }
        Object value = getCacheValue(key);
        return ApiResult.success(value);
    }

    @RequestMapping(value = {"/clearAll"}, method = {RequestMethod.POST, RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> clearAll() {
        clearAllCaches();
        return ApiResult.successNoResult();
    }

    @RequestMapping(value = {"/removeLike/{likeKey}"}, method = {RequestMethod.POST, RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<?> removeLike(@PathVariable("likeKey") String likeKey) {
        if (Strings.isEmpty(likeKey)) {
            return ApiResult.fail("LikeKey is empty");
        }
        List<String> keys = getAllCacheKeys();
        for (String k : keys) {
            if (k != null && k.contains(likeKey)) {
                CacheUtil.remove(k);
            }
        }
        return ApiResult.successNoResult();
    }

    private List<String> getAllCacheKeys() {
        try {
            Method m = null;
            String[] candidates = new String[]{"keys", "getKeys", "getAllKeys"};
            for (String name : candidates) {
                try {
                    m = CacheUtil.class.getMethod(name);
                    break;
                } catch (NoSuchMethodException ignored) {
                }
            }
            if (m != null) {
                Object res = m.invoke(null);
                List<String> list = new ArrayList<>();
                if (res instanceof Collection) {
                    for (Object o : (Collection<?>) res) {
                        if (o != null) {
                            list.add(String.valueOf(o));
                        }
                    }
                } else if (res instanceof Object[]) {
                    for (Object o : (Object[]) res) {
                        if (o != null) {
                            list.add(String.valueOf(o));
                        }
                    }
                }
                return list;
            }
        } catch (Exception ex) {
            log.error("list cache keys error", ex);
        }
        return new ArrayList<>();
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
            Method m = null;
            String[] candidates = new String[]{"clearAll", "clear", "removeAll"};
            for (String name : candidates) {
                try {
                    m = CacheUtil.class.getMethod(name);
                    break;
                } catch (NoSuchMethodException ignored) {
                }
            }
            if (m != null) {
                m.invoke(null);
                return;
            }
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
