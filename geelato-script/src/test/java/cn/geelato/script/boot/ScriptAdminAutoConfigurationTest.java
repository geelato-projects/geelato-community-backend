package cn.geelato.script.boot;

import cn.geelato.script.core.DefaultScriptEngine;
import cn.geelato.script.core.ScriptEngineBuilder;
import cn.geelato.script.rest.ScriptAdminController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptAdminAutoConfigurationTest {

    private static final DefaultScriptEngine ENGINE = new ScriptEngineBuilder().build();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ScriptAdminAutoConfiguration.class))
            .withBean(DefaultScriptEngine.class, () -> ENGINE);

    @Test
    void disabledByDefault() {
        runner.run(context -> assertThat(context).doesNotHaveBean(ScriptAdminController.class));
    }

    @Test
    void enabledWithProperty() {
        runner.withPropertyValues("geelato.script.admin.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(ScriptAdminController.class));
    }
}
