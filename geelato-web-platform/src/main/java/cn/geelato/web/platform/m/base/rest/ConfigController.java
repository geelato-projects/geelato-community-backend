package cn.geelato.web.platform.m.base.rest;

import jakarta.servlet.http.HttpServletRequest;
import cn.geelato.core.api.ApiResult;
import cn.geelato.core.constants.MediaTypes;
import cn.geelato.core.env.EnvManager;
import cn.geelato.core.env.entity.SysConfig;
import cn.geelato.utils.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping(value = "/api/config")
public class ConfigController extends BaseController {


    @RequestMapping(value = {""}, method = RequestMethod.GET, produces = MediaTypes.JSON_UTF_8)
    @ResponseBody
    public ApiResult list(HttpServletRequest request) {
        ApiResult result = new ApiResult();
        String tenantCode=request.getParameter("tenantCode");
        String appId=request.getParameter("appId");
        Map<String, SysConfig> configMap=EnvManager.singleInstance().getConfigMap("webapp");
        Map<String,Object> rtnConfigMap=new HashMap<>();
        Map<String,String> globalConfigMap=new HashMap<>();
        Map<String,String> tenantConfigMap=new HashMap<>();
        Map<String,String> appConfigMap=new HashMap<>();
        for (Map.Entry<String,SysConfig> entry: configMap.entrySet()) {
            SysConfig config = entry.getValue();
            if(StringUtils.isEmpty(config.getTenantCode())){
                globalConfigMap.put(config.getConfigKey(),config.getConfigValue());
                rtnConfigMap.put("sys",globalConfigMap);
            }
            if(!StringUtils.isEmpty(tenantCode)&&config.getTenantCode().equals(tenantCode)){
                tenantConfigMap.put(config.getConfigKey(),config.getConfigValue());
                rtnConfigMap.put("tenant",tenantConfigMap);
            }
            if(!StringUtils.isEmpty(appId)&&config.getAppId().equals(appId)){
                appConfigMap.put(config.getConfigKey(),config.getConfigValue());
                rtnConfigMap.put("app",appConfigMap);
            }

        }
        result.setData(rtnConfigMap);
        return result;
    }

    @RequestMapping(value = {"/refresh/{configKey}"}, method = RequestMethod.GET, produces = MediaTypes.JSON_UTF_8)
    @ResponseBody
    public ApiResult refresh(HttpServletRequest request,@PathVariable("configKey") String configKey) {
        ApiResult result = new ApiResult();
        EnvManager.singleInstance().refreshConfig(configKey);
        return result;
    }


}
