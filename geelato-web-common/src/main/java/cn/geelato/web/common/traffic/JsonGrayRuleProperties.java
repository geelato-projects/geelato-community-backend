package cn.geelato.web.common.traffic;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 灰度规则（JSON 形式，存 Redis）相关配置，前缀 {@code geelato.traffic.gray}。
 * <p>
 * 默认值均已内置，无需在 application.properties 中配置。运维只需操作 Redis key
 * （默认 {@link #redisKey}）写入规则 JSON，并调用 {@code /api/gray-rules/reload} 刷新。
 */
@Data
@Component
@ConfigurationProperties(prefix = "geelato.traffic.gray")
public class JsonGrayRuleProperties {

    /** 是否启用基于 JSON 规则的灰度判定。 */
    private boolean enabled = true;

    /** 存放灰度规则 JSON 数组的 Redis key。 */
    private String redisKey = "geelato:gray:rules";

    /** 百分比灰度哈希盐值，用于稳定地打散用户。 */
    private String hashSalt = "geelato";
}
