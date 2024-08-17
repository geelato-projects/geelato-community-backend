package cn.geelato.web.platform.m.base.rest;


import jakarta.servlet.http.HttpServletRequest;
import net.oschina.j2cache.CacheChannel;
import net.oschina.j2cache.CacheObject;
import net.oschina.j2cache.J2Cache;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.constants.MediaTypes;
import cn.geelato.web.platform.m.base.entity.CacheItemMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.*;

/**
 * :
 *
 * @author itechgee@126.com
 */
@Controller
@RequestMapping(value = "/api/cache/")
public class CacheController extends BaseController {

    private CacheChannel cache = J2Cache.getChannel();

    private MetaManager metaManager = MetaManager.singleInstance();


    private static Logger logger = LoggerFactory.getLogger(CacheController.class);

    /**
     * e.g.:http://localhost:8080/api/cache/list/
     *
     * @param request
     * @return
     */
    @RequestMapping(value = {"list", "list/*"}, method = RequestMethod.POST, produces = MediaTypes.JSON_UTF_8)
    @ResponseBody
    public ApiResult list(HttpServletRequest request) {
        ApiResult apiResult = new ApiResult();
        apiResult.setData(cache.regions());
        List<CacheObject> list = new ArrayList();
        for (CacheChannel.Region region : cache.regions()) {
            for (String key : cache.keys(region.getName())) {
                CacheObject cacheObject = cache.get(region.getName(), key);
                cacheObject.getKey();
                cacheObject.getLevel();
                cacheObject.getRegion();
                cacheObject.getValue();
                list.add(cacheObject);
            }
        }

        ApiPagedResult page = new ApiPagedResult();
        page.setDataSize(10);
        page.setPage(1000);
        page.setSize(10);
        page.setTotal(1000);
        page.setData(list);
        page.setMeta(metaManager.get(CacheItemMeta.class).getSimpleFieldMetas(new String[]{"region", "key", "level", "value"}));
        return page;
    }
}
