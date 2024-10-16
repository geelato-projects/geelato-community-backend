package cn.geelato.web.platform.m.base.rest;


import cn.geelato.core.constants.MediaTypes;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.cache.CacheUtil;
import cn.geelato.web.platform.m.BaseController;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;


/**
 * @author geelato
 */
@ApiRestController("/cache/")
@Slf4j
public class CacheController extends BaseController {

    /**
     * 清除缓存
     * keys: 多个key以英文逗号分隔, 不能为空值
     */
    @RequestMapping(value = {"remove/{keys}"}, method = {RequestMethod.POST, RequestMethod.GET}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
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

}
