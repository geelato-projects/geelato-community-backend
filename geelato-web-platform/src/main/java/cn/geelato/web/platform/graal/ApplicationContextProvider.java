package cn.geelato.web.platform.graal;

import cn.geelato.core.Ctx;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class ApplicationContextProvider implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        System.out.println("setApplicationContext: " + Ctx.getCurrentUser());
        context = applicationContext;
    }

    public static <T> T getBean(Class<T> requiredType) {
        System.out.println("setApplicationContext: " + Ctx.getCurrentUser());
        return context.getBean(requiredType);
    }
}
