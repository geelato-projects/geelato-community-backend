package cn.geelato.core.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

@Component
public class BeansUtils implements ApplicationContextAware {
    private static ApplicationContext context;

    public static <T> T getBean(Class<T> bean) {
        return context.getBean(bean);
    }

    public static <T> T getBean(String name, Class<T> type) {
        return context.getBean(name, type);
    }

    @Nullable
    public static ApplicationContext getApplicationContext() {
        return context;
    }

    @Nullable
    public static <T> T getBeanIfAvailable(Class<T> bean) {
        return context == null ? null : context.getBeanProvider(bean).getIfAvailable();
    }

    public static <T> Map<String, T> getBeansOfType(Class<T> type) {
        return context == null ? Collections.emptyMap() : context.getBeansOfType(type);
    }

    @Override
    public void setApplicationContext(@Nullable ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }
}
