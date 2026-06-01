package cn.geelato.datasource;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geelato.datasource.dynamic")
public class DynamicDataSourceProperties {
    private boolean delayLoadDataSource = true;
    private Integer minimumIdle = 1;
    private Integer maximumPoolSize = 10;
    private Long idleTimeoutMs = 600000L;
    private Long maxLifetimeMs = 1800000L;
    private Long connectionTimeoutMs = 5000L;
    private Long validationTimeoutMs = 3000L;
    private Long keepaliveTimeMs = 300000L;
    private Long initializationFailTimeoutMs = 0L;
    private String connectionTestQuery = "SELECT 1";

    public void setDelayLoadDataSource(boolean delayLoadDataSource) {
        this.delayLoadDataSource = true;
    }
}
