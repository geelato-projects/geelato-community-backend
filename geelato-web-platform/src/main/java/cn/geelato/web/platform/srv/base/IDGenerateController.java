package cn.geelato.web.platform.srv.base;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.utils.UIDGenerator;
import cn.geelato.web.common.annotation.ApiRestController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;

@ApiRestController("/id")
@Slf4j
public class IDGenerateController {
    @GetMapping("/generate")
    public ApiResult<?> generate() {
        return ApiResult.success(UIDGenerator.generate());
    }
}
