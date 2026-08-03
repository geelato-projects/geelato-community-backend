package cn.geelato.web.platform.plugin;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 插件开关配置文件路径解析。
 * <p>布局：
 * <pre>
 * {config-directory}/
 * ├── plugins-enabled.json
 * └── tenants/
 *     ├── tenant_geelato.json
 *     └── tenant_{code}.json
 * </pre>
 *
 * @author geelato
 */
@Component
public class PluginStatusPaths {

    private final PluginConfigurationProperties properties;

    public PluginStatusPaths(PluginConfigurationProperties properties) {
        this.properties = properties;
    }

    /** 平台级开关文件。 */
    public Path platformFile() {
        return Paths.get(properties.getConfigDirectory(), "plugins-enabled.json");
    }

    /** 租户级开关文件。 */
    public Path tenantFile(String tenantCode) {
        return Paths.get(properties.getConfigDirectory(), "tenants", "tenant_" + safe(tenantCode) + ".json");
    }

    /** 租户级开关目录。 */
    public Path tenantsDir() {
        return Paths.get(properties.getConfigDirectory(), "tenants");
    }

    private static String safe(String tenantCode) {
        // 防路径穿越：仅保留字母数字下划线中划线
        return tenantCode == null ? "unknown" : tenantCode.replaceAll("[^A-Za-z0-9_\\-]", "_");
    }
}
