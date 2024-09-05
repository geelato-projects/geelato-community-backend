package cn.geelato.web.platform.graal.service;

import cn.geelato.core.env.EnvManager;
import cn.geelato.core.env.entity.SysConfig;
import cn.geelato.core.graal.GraalService;
import cn.geelato.utils.StringUtils;

import java.util.HashMap;
import java.util.Map;

@GraalService(name = "cfg",built = "true")
public class ConfigService {
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
    public String getConfig(String key) {
        return EnvManager.singleInstance().getConfigValue(key);
    }
}
