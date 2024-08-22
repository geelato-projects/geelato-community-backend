package cn.geelato.web.platform.m.base.rest;

import cn.geelato.core.constants.MediaTypes;
import cn.geelato.core.env.EnvManager;
import cn.geelato.core.env.entity.SysConfig;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.enums.SysConfigPurposeEnum;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@ApiRestController("/config")
public class ConfigController extends BaseController {


    @RequestMapping(value = {""}, method = RequestMethod.GET, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<Map<String, Object>> list(HttpServletRequest request) {
        String tenantCode = request.getParameter("tenantCode");
        String appId = request.getParameter("appId");
        Map<String, SysConfig> configMap = new HashMap<>();
        pullAll(configMap, SysConfigPurposeEnum.WEBAPP.getValue());
        pullAll(configMap, SysConfigPurposeEnum.ALL.getValue());
        Map<String, Object> rtnConfigMap = new HashMap<>();
        Map<String, String> globalConfigMap = new HashMap<>();
        Map<String, String> tenantConfigMap = new HashMap<>();
        Map<String, String> appConfigMap = new HashMap<>();
        if (!configMap.isEmpty()) {
            for (Map.Entry<String, SysConfig> entry : configMap.entrySet()) {
                SysConfig config = entry.getValue();
                if (StringUtils.isEmpty(config.getTenantCode())) {
                    globalConfigMap.put(config.getConfigKey(), config.getConfigValue());
                    rtnConfigMap.put("sys", globalConfigMap);
                }
                if (StringUtils.isNotEmpty(tenantCode) && Objects.equals(config.getTenantCode(), tenantCode)) {
                    tenantConfigMap.put(config.getConfigKey(), config.getConfigValue());
                    rtnConfigMap.put("tenant", tenantConfigMap);
                }
                if (StringUtils.isNotEmpty(appId) && Objects.equals(config.getAppId(), appId)) {
                    appConfigMap.put(config.getConfigKey(), config.getConfigValue());
                    rtnConfigMap.put("app", appConfigMap);
                }
            }
        }
        return ApiResult.success(rtnConfigMap);
    }

    @RequestMapping(value = {"/refresh/{configKey}"}, method = RequestMethod.GET, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    public ApiResult<NullResult> refresh(@PathVariable("configKey") String configKey) {
        try {
            EnvManager.singleInstance().refreshConfig(configKey);
            return ApiResult.successNoResult();
        } catch (Exception ex) {
            return ApiResult.fail(ex.getMessage());
        }
    }

    private void pullAll(Map<String, SysConfig> configMap, String purpose) {
        Map<String, SysConfig> map = null;
        if (StringUtils.isNotEmpty(purpose)) {
            map = EnvManager.singleInstance().getConfigMap(purpose);
        }
        if (map != null && !map.isEmpty()) {
            configMap.putAll(map);
        }
    }
}
