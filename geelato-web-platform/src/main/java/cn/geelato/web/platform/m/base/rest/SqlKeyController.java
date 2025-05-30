package cn.geelato.web.platform.m.base.rest;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Map;

@ApiRestController("/sk")
@Slf4j
public class SqlKeyController extends BaseController {

    @RequestMapping(value = "/{key}", method = {RequestMethod.POST})
    public ApiResult<?> exec(@PathVariable("key") String key, String connectId, @RequestBody Map<String, Object> paramMap) {
        try {
            if (StringUtils.isNotBlank(connectId)) {
                switchDbByConnectId(connectId);
            }
            return ApiResult.success(dynamicDao.executeKey(key, paramMap));
        } catch (Exception e) {
            return ApiResult.fail(e.getMessage());
        }
    }
}
