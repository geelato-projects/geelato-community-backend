package cn.geelato.plugin;

import cn.geelato.plugin.example.Greeting;
import org.pf4j.DefaultPluginManager;
import org.pf4j.ExtensionFactory;
import org.pf4j.JarPluginManager;
import org.pf4j.PluginManager;
import org.pf4j.spring.SpringPluginManager;
import org.pf4j.update.DefaultUpdateRepository;
import org.pf4j.update.UpdateManager;
import org.pf4j.update.UpdateRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Configuration
public class PluginConfiguration {
    private SpringPluginManager springPluginManager;
//    private UpdateManager updateManager;
    @Bean
    public SpringPluginManager pluginManager(PluginConfigurationProperties pluginConfigurationProperties) {
        Path pluginDirectory = normalizeDirectory(pluginConfigurationProperties.getPluginDirectory(), "plugins");
        SpringPluginManager spm = new SpringPluginManager(pluginDirectory);
        springPluginManager=spm;
        return spm;
    }

    @Bean
    @DependsOn("pluginManager")
    public UpdateManager updateManager(PluginConfigurationProperties pluginConfigurationProperties){
        Path pluginRepository = normalizeDirectory(pluginConfigurationProperties.getPluginRepository(), "plugins/repository");
        UpdateManager um = new UpdateManager(springPluginManager, pluginRepository);
        List<UpdateRepository> pluginRepositories=um.getRepositories();
        return um;
    }


    public PluginConfiguration(){

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
