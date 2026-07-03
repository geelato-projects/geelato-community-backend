package cn.geelato.web.platform.graal;

import cn.geelato.core.SessionCtx;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class ApplicationContextProvider implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(@NotNull ApplicationContext applicationContext) throws BeansException {
        System.out.println("setApplicationContext: " + SessionCtx.getCurrentUser());
        context = applicationContext;
    }

    public static <T> T getBean(Class<T> requiredType) {
        System.out.println("setApplicationContext: " + SessionCtx.getCurrentUser());
        return context.getBean(requiredType);
    }
}
