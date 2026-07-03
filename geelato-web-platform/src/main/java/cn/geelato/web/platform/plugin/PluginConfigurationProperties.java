package cn.geelato.web.platform.plugin;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geelato.plugin")
public class PluginConfigurationProperties {
    private String pluginDirectory = "plugins";
    private String pluginRepository = "plugins/repository";
}

