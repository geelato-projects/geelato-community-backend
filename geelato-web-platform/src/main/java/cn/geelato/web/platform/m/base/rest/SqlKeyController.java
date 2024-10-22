package cn.geelato.web.platform.m.base.rest;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@ApiRestController("/sk")
@Slf4j
public class SqlKeyController extends BaseController {


    @RequestMapping(value = "/{key}", method = {RequestMethod.POST})
    public ApiResult<NullResult> exec(@PathVariable("key") String key,@RequestBody Map<String, Object> paramMap) {
        try {
            dao.executeKey(key, paramMap);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            return ApiResult.fail(e.getMessage());
        }
    }
}
