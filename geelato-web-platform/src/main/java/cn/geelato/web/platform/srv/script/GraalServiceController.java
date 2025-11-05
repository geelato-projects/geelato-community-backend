package cn.geelato.web.platform.srv.script;

import cn.geelato.core.graal.GraalManager;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@ApiRestController("/script/function")
public class GraalServiceController {

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public ApiResult<?> getGraalDescriptions() {
        return ApiResult.success(GraalManager.singleInstance().getGraalServiceDescriptions());
    }
}
