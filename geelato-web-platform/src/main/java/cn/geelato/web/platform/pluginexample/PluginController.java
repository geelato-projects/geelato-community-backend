package cn.geelato.web.platform.pluginexample;

import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.plugin.PluginBeanProvider;
import cn.geelato.plugin.example.Greeting;
import cn.geelato.plugin.example.PluginInfo;
import cn.geelato.web.platform.m.base.rest.BaseController;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping(value = "/api/plugin")
public class PluginController extends BaseController {
    private final Logger logger = LoggerFactory.getLogger(PluginController.class);
    @Autowired
    PluginBeanProvider pluginBeanProvider;

    @RequestMapping(value = "/example", method = RequestMethod.GET)
    @ResponseBody
    public ApiPagedResult example(HttpServletRequest req) {
        ApiPagedResult result = new ApiPagedResult();
        Greeting greeting = pluginBeanProvider.getBean(Greeting.class, PluginInfo.PluginId);
        result.setData(greeting.getGreeting());
        return result;
    }
}
