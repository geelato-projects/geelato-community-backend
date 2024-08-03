package cn.geelato.plugin;

import cn.geelato.plugin.example.Greeting;
import org.pf4j.DefaultPluginManager;
import org.pf4j.ExtensionFactory;
import org.pf4j.JarPluginManager;
import org.pf4j.PluginManager;
import org.pf4j.spring.SpringPluginManager;
import org.pf4j.update.UpdateManager;
import org.pf4j.update.UpdateRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Configuration
public class PluginConfiguration {
    private SpringPluginManager springPluginManager;
    private UpdateManager updateManager;
    @Bean
    public SpringPluginManager pluginManager() {
        SpringPluginManager spm=new SpringPluginManager(
                Paths.get("D:\\geelato-enterprise\\plugins\\")
        );
        spm.loadPlugins();
        spm.startPlugins();
        springPluginManager=spm;
        return spm;
    }

    @Bean
    @DependsOn("pluginManager")
    public UpdateManager updateManager(){
        UpdateManager um = new UpdateManager(springPluginManager);
        List<UpdateRepository> pluginRepositories=um.getRepositories();
        return um;
    }


    public PluginConfiguration(){

    }

}
