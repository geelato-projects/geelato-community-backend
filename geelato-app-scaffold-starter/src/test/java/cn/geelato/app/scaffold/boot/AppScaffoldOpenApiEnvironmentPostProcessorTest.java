package cn.geelato.app.scaffold.boot;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

public class AppScaffoldOpenApiEnvironmentPostProcessorTest {
    @Test
    void shouldDisableSpringdocByDefaultInProdWhenNotExposed() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.profiles.active", "prod")
                .withProperty("geelato.app.scaffold.openapi-enabled", "true")
                .withProperty("geelato.app.scaffold.openapi-expose-in-prod", "false");

        AppScaffoldOpenApiEnvironmentPostProcessor processor = new AppScaffoldOpenApiEnvironmentPostProcessor();
        processor.postProcessEnvironment(environment, new SpringApplication());

        Assertions.assertEquals("false", environment.getProperty("springdoc.api-docs.enabled"));
        Assertions.assertEquals("false", environment.getProperty("springdoc.swagger-ui.enabled"));
    }

    @Test
    void shouldNotOverrideWhenSpringdocAlreadyConfigured() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.profiles.active", "prod")
                .withProperty("geelato.app.scaffold.openapi-enabled", "true")
                .withProperty("geelato.app.scaffold.openapi-expose-in-prod", "false")
                .withProperty("springdoc.api-docs.enabled", "true");

        AppScaffoldOpenApiEnvironmentPostProcessor processor = new AppScaffoldOpenApiEnvironmentPostProcessor();
        processor.postProcessEnvironment(environment, new SpringApplication());

        Assertions.assertEquals("true", environment.getProperty("springdoc.api-docs.enabled"));
    }
}
