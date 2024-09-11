package cn.geelato.web.platform.pluginexample;

import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.api.ResultCode;
import cn.geelato.plugin.PluginBeanProvider;
import cn.geelato.plugin.example.Greeting;
import cn.geelato.plugin.example.PluginInfo;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.annotation.ApiRuntimeRestController;
import cn.geelato.web.platform.annotation.RuntimeMapping;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.expression.spel.ast.NullLiteral;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import static java.lang.constant.ConstantDescs.NULL;

@ApiRuntimeRestController("/plugin")
@Slf4j
public class PluginRuntimeController extends PluginController {

    @Autowired
    public PluginRuntimeController(PluginBeanProvider pluginBeanProvider) {
        super(pluginBeanProvider);
    }

    @Override
    @RequestMapping(value = "/example2", method = RequestMethod.GET)
    public ApiResult<Greeting> example(HttpServletRequest req) {
        Greeting greeting = pluginBeanProvider.getBean(Greeting.class, PluginInfo.PluginId);
        return ApiResult.success(greeting);
    }

    @RequestMapping(value = "/example3", method = RequestMethod.GET)
    public ApiResult<?> example3(HttpServletRequest req) {
        return ApiResult.fail("fail message");
    }
}
