package cn.geelato.plugin;

import cn.geelato.utils.StringUtils;
import org.pf4j.spring.SpringPluginManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PluginBeanProvider {
    private final SpringPluginManager springPluginManager;

    @Autowired
    public PluginBeanProvider(SpringPluginManager springPluginManager){
        this.springPluginManager=springPluginManager;
    }


    public <T> T getBean(Class<T> type,String pluginId){
        List<T> extensions=null;
        if(StringUtils.isEmpty(pluginId)){
            extensions=springPluginManager.getExtensions(type);
        }else{
            extensions=springPluginManager.getExtensions(type,pluginId);
        }
        if(extensions!=null&& !extensions.isEmpty()){
            return extensions.get(0);
        }else{
            throw new UnFoundPluginException();
        }

    }
}
