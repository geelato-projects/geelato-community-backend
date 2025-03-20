package cn.geelato.web.platform.m.security.wechat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geelato.wx")
public class WxChatConfiguration {
    private String url;
    private String appId;
    private String secret;
}
