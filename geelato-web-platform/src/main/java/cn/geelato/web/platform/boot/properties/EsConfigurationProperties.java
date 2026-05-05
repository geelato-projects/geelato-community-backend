package cn.geelato.web.platform.boot.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geelato.es")
public class EsConfigurationProperties {
    private Boolean enabled = false;
    private String url;
    private String username;
    private String password;
    private String indexPrefix;
    private Boolean logEnabled = true;
    private String logAppName = "geelato-web";
    private String logEnv = "dev";
    private String logIndexPrefix = "geelato-log-";
    private Integer logBulkSize = 200;
    private Integer logQueueSize = 10000;
    private Long logFlushIntervalMs = 2000L;
    private Integer logMaxRetry = 3;
    private Long logRetryBackoffMs = 1000L;
}
