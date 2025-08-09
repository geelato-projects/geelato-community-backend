package cn.geelato.web.platform.pm;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@ApiRestController("/pm")
public class PluginManagerController {
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public ApiResult<?> list() {
        return null;
    }
    @RequestMapping(value = "/switchStatus", method = RequestMethod.GET)
    public ApiResult<?> switchStatus(@RequestParam String status) {
        return null;
    }
    @RequestMapping(value = "/log", method = RequestMethod.GET)
    public ApiResult<?> log(String pluginId) {
        return null;
    }

}
