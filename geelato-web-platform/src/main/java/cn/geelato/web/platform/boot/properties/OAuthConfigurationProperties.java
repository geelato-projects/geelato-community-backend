package cn.geelato.web.platform.boot.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geelato.oauth")
public class OAuthConfigurationProperties {
    private String url;
    private String clientId;
    private String clientSecret;
}
