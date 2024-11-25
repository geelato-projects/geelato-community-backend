package cn.geelato.web.platform.m.pluginexample;

import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.plugin.PluginBeanProvider;
import cn.geelato.plugin.example.Greeting;
import cn.geelato.plugin.example.PluginInfo;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@ApiRestController("/plugin")
@Slf4j
public class PluginController extends BaseController {


    PluginBeanProvider pluginBeanProvider;

    @Autowired
    public PluginController(PluginBeanProvider pluginBeanProvider){
        this.pluginBeanProvider=pluginBeanProvider;
    }

    @RequestMapping(value = "/example", method = RequestMethod.GET)
    public ApiResult<String> example() {

        Map<String,Object> pars= getQueryParameters();
        Object par1= pars.get("par1");
        Object par2=pars.get("par2");
        Greeting greeting = pluginBeanProvider.getBean(Greeting.class, PluginInfo.PluginId);
        return ApiPagedResult.success(greeting.getGreeting());
    }
}
