package cn.geelato.web.platform.boot.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geelato.es")
public class EsConfigurationProperties {
    private String url;
    private String username;
    private String password;
    private String indexPrefix;
}
