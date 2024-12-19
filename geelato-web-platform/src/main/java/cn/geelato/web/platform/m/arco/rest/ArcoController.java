package cn.geelato.web.platform.m.arco.rest;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.arco.enums.ArcoEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@ApiRestController(value = "/arco")
@Slf4j
public class ArcoController extends BaseController {

    /**
     * 根据枚举码获取选择项数据
     *
     * @param code 枚举码
     * @return 包含选择项数据的ApiResult对象，如果找不到对应的枚举类，则返回空的ApiResult对象
     * @throws NoSuchMethodException     如果找不到指定名称的方法
     * @throws InvocationTargetException 如果被调用方法抛出异常
     * @throws IllegalAccessException    如果无法访问指定的方法
     */
    @RequestMapping(value = "/sod/{code}", method = RequestMethod.GET)
    public ApiResult<?> getSelectOptionData(@PathVariable(required = true) String code) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class<?> clazz = ArcoEnum.getClassByCode(code);
        if (clazz != null) {
            Method staticMethod = clazz.getMethod("getSelectOptions", String.class);
            return ApiResult.success(staticMethod.invoke(null));
        }
        return ApiResult.successNoResult();
    }
}
