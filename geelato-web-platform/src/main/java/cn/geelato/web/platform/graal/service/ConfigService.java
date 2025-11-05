package cn.geelato.web.platform.graal.service;

import cn.geelato.core.env.EnvManager;
import cn.geelato.core.env.entity.SysConfig;
import cn.geelato.core.graal.GraalFunction;
import cn.geelato.core.graal.GraalService;
import cn.geelato.utils.StringUtils;

import java.util.HashMap;
import java.util.Map;

@GraalService(name = "cfg",built = "true", descrption = "平台配置相关")
public class ConfigService {
    @GraalFunction(example = "$gl.cfg.queryTenantConfig({tenantCode})", description = "根据租户编码返回租户配置键值")
    public Map<String,String> queryTenantConfig(String tenantCode) {
        var allConfig=EnvManager.singleInstance().getAllConfig();
        Map<String,String> tenantConfig=new HashMap<>();
        for (Map.Entry<String, SysConfig> entry : allConfig.entrySet()) {
            SysConfig config = entry.getValue();
            if (StringUtils.isNotEmpty(tenantCode) && StringUtils.isNotBlank(config.getTenantCode()) && config.getTenantCode().equals(tenantCode)) {
                tenantConfig.put(config.getConfigKey(), config.getConfigValue());
            }
        }
        return tenantConfig;
    }

    @GraalFunction(example = "$gl.cfg.queryAppConfig({appId})", description = "根据应用ID返回应用级配置键值")
    public Map<String,String> queryAppConfig(String appId) {
        var allConfig=EnvManager.singleInstance().getAllConfig();
        Map<String,String> appConfig=new HashMap<>();
        for (Map.Entry<String, SysConfig> entry : allConfig.entrySet()) {
            SysConfig config = entry.getValue();
            if (StringUtils.isNotEmpty(appId) && StringUtils.isNotBlank(config.getAppId()) && config.getAppId().equals(appId)) {
                appConfig.put(config.getConfigKey(), config.getConfigValue());
            }
        }
        return appConfig;
    }

    @GraalFunction(example = "$gl.cfg.queryPlatformConfig()", description = "返回平台级（无租户）配置键值")
    public Map<String,String> queryPlatformConfig() {
        var allConfig=EnvManager.singleInstance().getAllConfig();
        Map<String,String> platformConfig=new HashMap<>();
        for (Map.Entry<String, SysConfig> entry : allConfig.entrySet()) {
            SysConfig config = entry.getValue();
            if (StringUtils.isEmpty(config.getTenantCode())) {
                platformConfig.put(config.getConfigKey(), config.getConfigValue());
            }
        }
        return platformConfig;
    }
    @GraalFunction(example = "$gl.cfg.getConfig({key})", description = "根据配置键获取对应的配置值")
    public String getConfig(String key) {
        return EnvManager.singleInstance().getConfigValue(key);
    }
}
