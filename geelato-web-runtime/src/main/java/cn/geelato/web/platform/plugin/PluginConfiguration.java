package cn.geelato.web.platform.plugin;

import org.pf4j.spring.SpringPluginManager;
import org.pf4j.update.UpdateManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class PluginConfiguration {
    private SpringPluginManager springPluginManager;

    @Bean
    public SpringPluginManager pluginManager(PluginConfigurationProperties pluginConfigurationProperties) {
        Path pluginDirectory = normalizeDirectory(pluginConfigurationProperties.getPluginDirectory(), "plugins");
        SpringPluginManager spm = new SpringPluginManager(pluginDirectory);
        springPluginManager = spm;
        return spm;
    }

    @Bean
    @DependsOn("pluginManager")
    public UpdateManager updateManager(PluginConfigurationProperties pluginConfigurationProperties) {
        Path pluginRepository = normalizeDirectory(pluginConfigurationProperties.getPluginRepository(), "plugins/repository");
        return new UpdateManager(springPluginManager, pluginRepository);
    }

    public PluginConfiguration() {
    }

    public SpringPluginManager getSpringPluginManager() {
        return springPluginManager;
    }

    private Path normalizeDirectory(String pathValue, String defaultPath) {
        String candidate = (pathValue == null || pathValue.trim().isEmpty()) ? defaultPath : pathValue.trim();
        Path path = Paths.get(candidate);
        try {
            Files.createDirectories(path);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize plugin path: " + path, e);
        }
        return path;
    }
}

