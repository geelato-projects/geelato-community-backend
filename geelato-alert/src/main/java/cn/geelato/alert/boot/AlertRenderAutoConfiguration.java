package cn.geelato.alert.boot;

import cn.geelato.alert.engine.TplService;
import cn.geelato.script.boot.ScriptEngineCustomizer;
import cn.geelato.script.core.ScriptProfile;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Set;

/**
 * 告警渲染装配：alert-render profile 不限定命名空间与变量（注册到的注解 kit 全部可见），
 * 仅收紧超时预算（bea 设计：模板单脚本 ≤ 2s）并冻结 Map 型绑定（上下文只读）。
 */
@AutoConfiguration
@ConditionalOnClass(cn.geelato.script.core.ScriptEngine.class)
@EnableConfigurationProperties(AlertRenderProperties.class)
public class AlertRenderAutoConfiguration {

    @Bean
    public ScriptEngineCustomizer alertScriptEngineCustomizer(AlertRenderProperties properties) {
        return builder -> builder
                .profile(ScriptProfile.builder()
                        .id("alert-render")
                        .namespaces(Set.of(ScriptProfile.ALL))
                        .variables(Set.of(ScriptProfile.ALL))
                        .frozenBindings(true)
                        .defaultTimeoutMs(properties.getTimeoutMs())
                        .build())
                .registerKit(new TplService(properties.getWorkbenchBaseUrl()));
    }
}
