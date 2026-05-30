package cn.geelato.web.platform.srvlog.boot;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geelato.srv-log")
public class SrvLogProperties {
    private Boolean enabled = true;
    private Boolean recordSuccess = true;
    private String storeType = "es";
    private String esIndexPrefix = "geelato-srv-log-";
    private String fileDir;
    private Integer maxFieldLength = -1;
}

