package cn.geelato.web.platform.srv.example;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.plugin.PluginBeanProvider;
import cn.geelato.plugin.example.Greeting;
import cn.geelato.plugin.example.PluginInfo;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@ApiRuntimeRestController("/plugin")
@Slf4j
public class PluginRuntimeController extends PluginController {

    @Autowired
    public PluginRuntimeController(PluginBeanProvider pluginBeanProvider) {
        super(pluginBeanProvider);
    }

    @Override
    @RequestMapping(value = "/example2", method = RequestMethod.GET)
    public ApiResult<String> example() {
        Greeting greeting = pluginBeanProvider.getBean(Greeting.class, PluginInfo.PluginId);
        return ApiResult.success(greeting.getGreeting());
    }

    @RequestMapping(value = "/example3", method = RequestMethod.GET)
    public ApiResult<?> example3() {
        return ApiResult.fail("fail message");
    }
}
