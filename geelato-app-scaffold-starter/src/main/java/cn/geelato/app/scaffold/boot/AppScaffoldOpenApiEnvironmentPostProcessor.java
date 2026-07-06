package cn.geelato.app.scaffold.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

import java.util.LinkedHashMap;
import java.util.Map;

public class AppScaffoldOpenApiEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        AppScaffoldProperties properties = Binder.get(environment)
                .bind("geelato.app.scaffold", Bindable.of(AppScaffoldProperties.class))
                .orElseGet(AppScaffoldProperties::new);

        boolean prod = environment.acceptsProfiles(Profiles.of("prod", "production"));
        boolean openapiAllowed = properties.isOpenapiEnabled() && (!prod || properties.isOpenapiExposeInProd());

        if (openapiAllowed) {
            return;
        }

        if (environment.containsProperty("springdoc.api-docs.enabled")
                || environment.containsProperty("springdoc.swagger-ui.enabled")) {
            return;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("springdoc.api-docs.enabled", "false");
        values.put("springdoc.swagger-ui.enabled", "false");
        values.put("springdoc.swagger-ui.path", "/swagger-ui/index.html");

        environment.getPropertySources().addFirst(new MapPropertySource("geelato-app-scaffold-openapi-defaults", values));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

