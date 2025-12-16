package cn.geelato.web.platform.srv.base;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

import java.util.*;

@ApiRestController("/system/init")
@Slf4j
public class SystemInitController {

    private final ConfigurableEnvironment environment;

    public SystemInitController(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    @RequestMapping(value = {"/properties"}, method = {RequestMethod.GET})
    public ApiResult<?> properties() {
        Map<String, Map<String, String>> result = new HashMap<>();
        for (PropertySource<?> ps : environment.getPropertySources()) {
            String name = ps.getName();
            if (!(ps instanceof EnumerablePropertySource<?> eps)) continue;
            if (!isConfigFileSource(name)) continue;
            String category = categoryOfSource(name);
            Map<String, String> bucket = result.computeIfAbsent(category, k -> new LinkedHashMap<>());
            for (String key : eps.getPropertyNames()) {
                String val = null;
                try {
                    Object v = environment.getProperty(key);
                    if (v != null) val = String.valueOf(v);
                } catch (Exception ignored) {
                }
                if (val == null) {
                    Object raw = eps.getProperty(key);
                    if (raw != null) {
                        String s = String.valueOf(raw);
                        if (!s.contains("${")) val = s;
                    }
                }
                if (val == null) val = "未配置";
                if (key != null) {
                    String k = key.toLowerCase(Locale.ROOT);
                    if (k.contains("password") || k.contains("secret")) val = "******";
                }
                bucket.put(key, val);
            }
        }
        return ApiResult.success(result);
    }

    private boolean isConfigFileSource(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains(".properties") || n.contains("application.yml") || n.contains("application.yaml") || n.contains("application.properties");
    }

    private String categoryOfSource(String sourceName) {
        String n = sourceName.toLowerCase(Locale.ROOT);
        Map<String, String> mapping = Map.of(
            "auth.properties", "auth",
            "market.properties", "market",
            "message.properties", "message",
            "oss.properties", "oss",
            "package.properties", "package",
            "sc.properties", "sc",
            "seata.properties", "seata",
            "weixin_work.properties", "weixin_work",
            "workflow.properties", "workflow"
        );
        for (Map.Entry<String, String> e : mapping.entrySet()) {
            if (n.contains(e.getKey())) return e.getValue();
        }
        return "default";
    }
}
