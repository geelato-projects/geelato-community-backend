package cn.geelato.web.platform.srv.site.utils;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geelato.sc")
public class StaticSiteConfiguration {
    private String folder;
    private String path;
}
