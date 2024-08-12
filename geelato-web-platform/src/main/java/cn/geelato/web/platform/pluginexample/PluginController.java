package cn.geelato.web.platform.pluginexample;

import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.plugin.PluginBeanProvider;
import cn.geelato.plugin.example.Greeting;
import cn.geelato.plugin.example.PluginInfo;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.base.rest.BaseController;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@ApiRestController("/plugin")
@Slf4j
public class PluginController extends BaseController {


    PluginBeanProvider pluginBeanProvider;

    @Autowired
    public PluginController(PluginBeanProvider pluginBeanProvider){
        this.pluginBeanProvider=pluginBeanProvider;
    }

    @RequestMapping(value = "/example", method = RequestMethod.GET)
    public ApiResult<Greeting> example(HttpServletRequest req) {
        Greeting greeting = pluginBeanProvider.getBean(Greeting.class, PluginInfo.PluginId);
        return ApiPagedResult.success(greeting);
    }
}
