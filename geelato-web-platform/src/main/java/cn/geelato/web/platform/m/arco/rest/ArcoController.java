package cn.geelato.web.platform.m.arco.rest;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.arco.enums.ArcoEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@ApiRestController(value = "/arco")
@Slf4j
public class ArcoController extends BaseController {

    /**
     * 根据枚举码获取选择项数据
     *
     * @param code 枚举码
     * @return 包含选择项数据的ApiResult对象，如果找不到对应的枚举类，则返回空的ApiResult对象
     */
    @RequestMapping(value = "/sod/{code}", method = RequestMethod.GET)
    public ApiResult<?> getSelectOptionData(@PathVariable() String code) {
        return ApiResult.success(ArcoEnum.getSelectOptions(code));
    }
}
