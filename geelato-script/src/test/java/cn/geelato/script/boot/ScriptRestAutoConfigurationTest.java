package cn.geelato.script.boot;

import cn.geelato.script.core.ScriptEngine;
import cn.geelato.script.core.ScriptEngineBuilder;
import cn.geelato.script.rest.ScriptCatalogController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptRestAutoConfigurationTest {

    private static final ScriptEngine ENGINE = new ScriptEngineBuilder().build();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ScriptRestAutoConfiguration.class))
            .withBean(ScriptEngine.class, () -> ENGINE);

    @Test
    void disabledByDefault() {
        runner.run(context -> assertThat(context).doesNotHaveBean(ScriptCatalogController.class));
    }

    @Test
    void enabledWithProperty() {
        runner.withPropertyValues("geelato.script.rest.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ScriptCatalogController.class);
                    assertThat(context).hasSingleBean(cn.geelato.script.rest.ScriptExecuteController.class);
                    assertThat(context).hasSingleBean(cn.geelato.script.rest.ScriptPlaygroundController.class);
                });
    }
}
