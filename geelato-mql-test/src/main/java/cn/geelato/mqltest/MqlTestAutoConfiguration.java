package cn.geelato.mqltest;

import cn.geelato.mqltest.explain.MqlExplainController;
import cn.geelato.mqltest.explain.MqlExplainService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

/**
 * MQL 工具链自动配置。
 * <p>
 * 仅在 {@code geelato.mql.playground.enabled=true} 时装配 explain Controller 与 Service。
 * <p>
 * 使用方式：在应用配置中设置 {@code geelato.mql.playground.enabled=true} 即可启用调试端点。
 * 建议仅在开发/测试环境开启。
 */
@AutoConfiguration
@ConditionalOnProperty(name = "geelato.mql.playground.enabled", havingValue = "true")
@Import({MqlExplainService.class, MqlExplainController.class})
public class MqlTestAutoConfiguration {
}
