package cn.geelato.script.boot;

import cn.geelato.script.core.ScriptEngine;
import cn.geelato.script.core.ScriptDebugService;
import cn.geelato.script.rest.ScriptCatalogController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * script-rest 可选装配：geelato.script.rest.enabled=true 时提供 /api/script/catalog、/api/script/versions
 * 与通用执行端点 /api/script/execute、/api/script/parse（测试/调试辅助，仅限内网可信环境），
 * 以及调试台页面 /script-playground.html。
 */
@AutoConfiguration
@ConditionalOnClass(org.springframework.web.bind.annotation.RestController.class)
@ConditionalOnProperty(name = "geelato.script.rest.enabled", havingValue = "true")
public class ScriptRestAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ScriptCatalogController scriptCatalogController(ScriptEngine scriptEngine) {
        return new ScriptCatalogController(scriptEngine);
    }

    @Bean
    @ConditionalOnMissingBean
    public cn.geelato.script.rest.ScriptExecuteController scriptExecuteController(ScriptEngine scriptEngine) {
        return new cn.geelato.script.rest.ScriptExecuteController(scriptEngine);
    }

    @Bean
    @ConditionalOnMissingBean
    public cn.geelato.script.rest.ScriptDebugController scriptDebugController(ScriptDebugService scriptDebugService) {
        return new cn.geelato.script.rest.ScriptDebugController(scriptDebugService);
    }

    @Bean
    @ConditionalOnMissingBean
    public cn.geelato.script.rest.ScriptPlaygroundController scriptPlaygroundController() {
        return new cn.geelato.script.rest.ScriptPlaygroundController();
    }
}
