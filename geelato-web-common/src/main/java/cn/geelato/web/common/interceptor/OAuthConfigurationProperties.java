package cn.geelato.web.common.interceptor;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geelato.oauth2")
public class OAuthConfigurationProperties {
    private String url;
    private String clientId;
    private String clientSecret;
}
