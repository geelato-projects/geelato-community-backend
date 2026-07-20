package cn.geelato.mqltest;

import cn.geelato.mqltest.explain.MqlExplainController;
import cn.geelato.mqltest.explain.MqlExplainService;
import cn.geelato.mqltest.explain.MqlIdentitySupport;
import cn.geelato.mqltest.explain.MqlPlaygroundController;
import cn.geelato.mqltest.explain.MqlTestScenarioService;
import cn.geelato.mqltest.model.MqlTestModelRegistrar;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MQL 工具链自动配置。
 * <p>
 * 仅在 {@code geelato.mql.playground.enabled=true} 时装配。
 * <p>
 * <ul>
 *   <li>{@link MqlExplainService} / {@link MqlExplainController}：dry-run + 元数据（无数据源也可用）</li>
 *   <li>{@link MqlTestScenarioService}：真实执行 + 场景验证（需 JdbcTemplate Bean，即宿主配了数据源）</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnProperty(name = "geelato.mql.playground.enabled", havingValue = "true")
@Import({MqlExplainService.class, MqlExplainController.class, MqlPlaygroundController.class})
public class MqlTestAutoConfiguration {

    /**
     * 启动时注册测试实体元数据到 MetaManager。
     */
    @PostConstruct
    public void registerTestModels() {
        MqlTestModelRegistrar.register();
    }

    /**
     * 场景执行服务仅在 JdbcTemplate Bean 存在时装配（宿主配置了数据源）。
     * <p>
     * 使用 primaryJdbcTemplate（主数据源）。宿主可能存在多个 JdbcTemplate Bean
     * （primary/secondary/dynamic），必须用 @Qualifier 指定，否则注入歧义。
     */
    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    public MqlTestScenarioService mqlTestScenarioService(
            @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return new MqlTestScenarioService(jdbcTemplate);
    }

    /**
     * 模拟身份支持（仅在 JdbcTemplate Bean 存在时装配）。
     * 用于 Playground 以指定身份执行，使租户/数据权限注入器照常工作。
     */
    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    public MqlIdentitySupport mqlIdentitySupport(
            @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbcTemplate,
            org.springframework.beans.factory.ObjectProvider<cn.geelato.security.OrgProvider> orgProviderProvider) {
        return new MqlIdentitySupport(jdbcTemplate, orgProviderProvider.getIfAvailable());
    }
}
