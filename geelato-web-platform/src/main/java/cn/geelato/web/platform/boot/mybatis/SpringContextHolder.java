package cn.geelato.web.platform.boot.mybatis;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class SpringContextHolder implements ApplicationContextAware {

    @Getter
    private static ConfigurableApplicationContext applicationContext;

    @Override
    public void setApplicationContext(@NotNull ApplicationContext context) throws BeansException {
        if (context instanceof ConfigurableApplicationContext) {
            applicationContext = (ConfigurableApplicationContext) context;
        } else {
            throw new RuntimeException("ApplicationContext is not ConfigurableApplicationContext");
        }
    }

    public static <T> T getBean(Class<T> clazz) {
        if (applicationContext == null) {
            throw new IllegalStateException("Spring 容器未初始化");
        }
        return applicationContext.getBean(clazz);
    }
}