package cn.geelato.script.boot;

import cn.geelato.script.core.EngineAdmin;
import cn.geelato.script.core.ScriptEngine;
import cn.geelato.script.rest.ScriptAdminController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * script-admin 可选装配：geelato.script.admin.enabled=true 时提供 /api/script/admin/*（版本查询与运行时管理）。
 */
@AutoConfiguration
@ConditionalOnClass(org.springframework.web.bind.annotation.RestController.class)
@ConditionalOnProperty(name = "geelato.script.admin.enabled", havingValue = "true")
public class ScriptAdminAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ScriptAdminController scriptAdminController(ScriptEngine scriptEngine, EngineAdmin engineAdmin) {
        return new ScriptAdminController(scriptEngine, engineAdmin);
    }
}
