package cn.geelato.datasource;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "geelato.datasource.dynamic")
public class DynamicDataSourceProperties {
    private boolean delayLoadDataSource = true;
    private boolean enableJtaTransaction = false;
    private boolean enableSeataProxy = false;
    private Integer minimumIdle = 1;
    private Integer maximumPoolSize = 10;
    private Long idleTimeoutMs = 600000L;
    private Long maxLifetimeMs = 1800000L;
    private Long connectionTimeoutMs = 5000L;
    private Long validationTimeoutMs = 3000L;
    private Long keepaliveTimeMs = 300000L;
    private Long initializationFailTimeoutMs = 0L;
    private String connectionTestQuery = "SELECT 1";
    /**
     * catalog（逻辑数据库分组）到数据源 connectId 的映射。
     * <p>
     * 对应配置：
     * <pre>
     * geelato:
     *   datasource:
     *     dynamic:
     *       catalog-mapping:
     *         platform: primary
     *         business: biz_db
     * </pre>
     * 实体声明 {@code @Entity(catalog="business")} 时，路由到 {@code biz_db} 数据源。
     * </p>
     */
    private Map<String, String> catalogMapping;
}
