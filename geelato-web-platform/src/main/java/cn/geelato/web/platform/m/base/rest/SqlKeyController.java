package cn.geelato.web.platform.m.base.rest;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.web.platform.annotation.ApiRestController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

@ApiRestController("/sk")
@Slf4j
public class SqlKeyController extends BaseController {


    @RequestMapping("/{key}")
    public ApiResult<NullResult> exec(@PathVariable("key") String key, Map<String, Object> paramMap) {
        try {
            dao.executeKey(key, paramMap);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            return ApiResult.fail(e.getMessage());
        }
    }
}
