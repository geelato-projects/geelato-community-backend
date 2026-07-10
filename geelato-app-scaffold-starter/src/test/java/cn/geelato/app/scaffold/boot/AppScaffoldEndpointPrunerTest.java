package cn.geelato.app.scaffold.boot;

import cn.geelato.web.common.annotation.ApiRestController;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.stream.Collectors;

public class AppScaffoldEndpointPrunerTest {

    @Test
    void shouldPruneDisallowedControllersByDefault() {
        MockEnvironment environment = new MockEnvironment();

        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        registerController(registry, "allowedJwt", "cn.geelato.web.platform.srv.auth.JWTAuthController");
        registerController(registry, "allowedMql", "cn.geelato.web.platform.srv.meta.MetaRuntimeController");
        registerController(registry, "disallowedAi", "cn.geelato.web.platform.srv.ai.AiController");

        AppScaffoldEndpointPruner pruner = new AppScaffoldEndpointPruner(environment, new AppScaffoldControllerCatalog());
        pruner.setBeanClassLoader(Thread.currentThread().getContextClassLoader());
        pruner.postProcessBeanDefinitionRegistry(registry);

        Assertions.assertTrue(registry.containsBeanDefinition("allowedJwt"));
        Assertions.assertTrue(registry.containsBeanDefinition("allowedMql"));
        Assertions.assertFalse(registry.containsBeanDefinition("disallowedAi"));
    }

    @Test
    void apiPrefixShouldApplyToApiRestController() {
        WebApplicationContextRunner runner = new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        WebMvcAutoConfiguration.class,
                        AppScaffoldApiPrefixAutoConfiguration.class
                ))
                .withUserConfiguration(DummyApiRestController.class);

        runner.run(context -> {
            RequestMappingHandlerMapping mapping = context.getBean(RequestMappingHandlerMapping.class);
            String mappings = mapping.getHandlerMethods().keySet().stream()
                    .flatMap(info -> info.getPatternValues().stream())
                    .sorted()
                    .collect(Collectors.joining(","));

            Assertions.assertTrue(mappings.contains("/api/dummy/ping"));
        });
    }

    private void registerController(BeanDefinitionRegistry registry, String beanName, String className) {
        GenericBeanDefinition bd = new GenericBeanDefinition();
        bd.setBeanClassName(className);
        registry.registerBeanDefinition(beanName, bd);
    }

    @ApiRestController("/dummy")
    static class DummyApiRestController {
        @GetMapping("/ping")
        public String ping() {
            return "pong";
        }
    }
}
