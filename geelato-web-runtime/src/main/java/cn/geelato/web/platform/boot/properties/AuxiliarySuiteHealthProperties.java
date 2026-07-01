package cn.geelato.web.platform.boot.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geelato.monitor.auxiliary-suites")
public class AuxiliarySuiteHealthProperties {
    private Boolean enabled = false;
    private Integer pollIntervalSeconds = 10;
    private Integer connectTimeoutSeconds = 5;
    private Integer readTimeoutSeconds = 10;
    private String suitesJson = "[]";
}
