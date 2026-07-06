package cn.geelato.app.scaffold.boot;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@ConditionalOnProperty(prefix = "geelato.app.scaffold", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AppScaffoldEndpointPrunerAutoConfiguration {
    @Bean
    public AppScaffoldControllerCatalog appScaffoldControllerCatalog() {
        return new AppScaffoldControllerCatalog();
    }

    @Bean
    public AppScaffoldEndpointPruner appScaffoldEndpointPruner(Environment environment, AppScaffoldControllerCatalog catalog) {
        return new AppScaffoldEndpointPruner(environment, catalog);
    }
}

