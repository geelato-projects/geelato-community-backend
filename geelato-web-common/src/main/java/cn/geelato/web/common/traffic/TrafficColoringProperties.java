package cn.geelato.web.common.traffic;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geelato.traffic")
public class TrafficColoringProperties {
    private boolean enabled = true;

    private String defaultTag = "default";
    private String grayTag = "gray";

    private String tagCookieName = "gl_traffic_tag";
    private String tagHeaderName = "X-Gl-Traffic-Tag";

    private String overrideHeaderName = "X-Gl-Traffic-Override";
    private String overrideQueryName = "glTrafficTag";

    private String requestAttributeKey = "gl.traffic.tag";
    private String mdcKey = "trafficTag";

    private String grayWhitelist;
    private String grayWhitelistLocation;

    private String cookiePath = "/";
    private String cookieDomain;
    private int cookieMaxAgeSeconds = 30 * 24 * 60 * 60;
    private boolean cookieHttpOnly = true;
    private boolean cookieSecure = false;

    private boolean signingEnabled = true;
    private String signingSecret;
}
