package cn.geelato.web.platform.boot;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * 获取属性文件信息
 * @author geelato
 */
@Configuration
public class BaseConfiguration implements ApplicationContextAware {

    protected ApplicationContext applicationContext;
    @Override
    public void setApplicationContext(@NotNull ApplicationContext applicationContext) throws BeansException {
       this.applicationContext = applicationContext;
    }

    protected String getProperty(String key,String defaultValue){
        return applicationContext.getEnvironment().getProperty(key,defaultValue);
    }
}
