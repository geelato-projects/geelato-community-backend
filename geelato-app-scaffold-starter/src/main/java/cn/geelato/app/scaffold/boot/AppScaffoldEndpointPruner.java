package cn.geelato.app.scaffold.boot;

import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import cn.geelato.web.common.annotation.DesignTimeApiRestController;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.Set;

public class AppScaffoldEndpointPruner implements BeanDefinitionRegistryPostProcessor, PriorityOrdered, BeanClassLoaderAware {
    private final Environment environment;
    private final AppScaffoldControllerCatalog controllerCatalog;
    private ClassLoader beanClassLoader;

    public AppScaffoldEndpointPruner(Environment environment, AppScaffoldControllerCatalog controllerCatalog) {
        this.environment = environment;
        this.controllerCatalog = controllerCatalog;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.beanClassLoader = classLoader;
    }

    @Override
    public int getOrder() {
        return PriorityOrdered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        AppScaffoldProperties properties = Binder.get(environment)
                .bind("geelato.app.scaffold", Bindable.of(AppScaffoldProperties.class))
                .orElseGet(AppScaffoldProperties::new);

        Set<String> allowedControllers = resolveAllowedControllers(properties);

        String[] beanNames = registry.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            if (!registry.containsBeanDefinition(beanName)) {
                continue;
            }
            BeanDefinition beanDefinition = registry.getBeanDefinition(beanName);
            String beanClassName = beanDefinition.getBeanClassName();
            if (beanClassName == null) {
                continue;
            }
            if (!beanClassName.startsWith("cn.geelato.web.platform.srv.")) {
                continue;
            }
            if (allowedControllers.contains(beanClassName)) {
                continue;
            }
            Class<?> beanClass = tryLoad(beanClassName);
            if (beanClass == null) {
                continue;
            }
            if (!isController(beanClass)) {
                continue;
            }
            registry.removeBeanDefinition(beanName);
        }
    }

    @Override
    public void postProcessBeanFactory(org.springframework.beans.factory.config.ConfigurableListableBeanFactory beanFactory) throws BeansException {
    }

    private Set<String> resolveAllowedControllers(AppScaffoldProperties properties) {
        Set<String> allowed = new HashSet<>();
        allowed.add("cn.geelato.app.scaffold.boot.AppScaffoldReadyController");

        for (AppScaffoldCapability capability : AppScaffoldCapability.builtinCapabilities()) {
            allowed.addAll(controllerCatalog.controllersBy(capability));
        }

        java.util.List<String> extras = properties.getExtraControllers();
        if (extras != null) {
            for (String extra : extras) {
                if (extra != null && !extra.isBlank()) {
                    allowed.add(extra.trim());
                }
            }
        }
        return allowed;
    }

    private boolean isController(Class<?> beanClass) {
        return AnnotatedElementUtils.hasAnnotation(beanClass, ApiRestController.class)
                || AnnotatedElementUtils.hasAnnotation(beanClass, DesignTimeApiRestController.class)
                || AnnotatedElementUtils.hasAnnotation(beanClass, ApiRuntimeRestController.class)
                || AnnotatedElementUtils.hasAnnotation(beanClass, RestController.class)
                || AnnotatedElementUtils.hasAnnotation(beanClass, Controller.class);
    }

    private Class<?> tryLoad(String className) {
        try {
            return ClassUtils.forName(className, beanClassLoader);
        } catch (Throwable ex) {
            return null;
        }
    }
}
