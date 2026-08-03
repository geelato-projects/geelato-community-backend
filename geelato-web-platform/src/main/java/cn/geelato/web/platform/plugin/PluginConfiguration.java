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

    /**
     * 平台级插件状态提供者（基于共享卷 JSON）。
     * 作为普通实例由 {@link #pluginManager(PluginConfigurationProperties)} 内部使用。
     */
    @Bean
    public FilePluginStatusProvider filePluginStatusProvider(PluginStatusPaths paths, PluginStatusJsonStore store) {
        return new FilePluginStatusProvider(paths, store);
    }

    @Bean
    public SpringPluginManager pluginManager(PluginConfigurationProperties pluginConfigurationProperties,
                                             FilePluginStatusProvider filePluginStatusProvider) {
        Path pluginDirectory = normalizeDirectory(pluginConfigurationProperties.getPluginDirectory(), "plugins");
        // pf4j 在构造期即调 createPluginStatusProvider()，需在 new 之前把 provider 放入 ThreadLocal
        FileSpringPluginManager.PROVIDER_HOLDER.set(filePluginStatusProvider);
        FileSpringPluginManager spm = new FileSpringPluginManager(pluginDirectory, filePluginStatusProvider,
                pluginConfigurationProperties.isSignatureVerify());
        // 构造器内已 remove；此处兜底再清一次，防异常路径泄漏
        FileSpringPluginManager.PROVIDER_HOLDER.remove();
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
