package cn.geelato.web.platform.m.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geelato.wx")
public class WXConfiguration {
    private String url;
    private String appId;
    private String secret;
}
