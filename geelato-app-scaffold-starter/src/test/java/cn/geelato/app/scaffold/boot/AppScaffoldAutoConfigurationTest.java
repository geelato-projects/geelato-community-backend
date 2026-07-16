package cn.geelato.app.scaffold.boot;

import cn.geelato.web.platform.boot.BootApplication;
import cn.geelato.web.platform.boot.MetaConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

public class AppScaffoldAutoConfigurationTest {

    @Test
    void shouldMakeMetaConfigurationDependOnSchemaInitializer() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("appScaffoldSchemaInitializer", new RootBeanDefinition(AppScaffoldSchemaInitializer.class));
        beanFactory.registerBeanDefinition("customMetaConfiguration", new RootBeanDefinition(MetaConfiguration.class));

        new AppScaffoldAutoConfiguration.AppScaffoldSchemaInitializerDependsOnPostProcessor()
                .postProcessBeanFactory(beanFactory);

        Assertions.assertArrayEquals(new String[]{"appScaffoldSchemaInitializer"},
                beanFactory.getBeanDefinition("customMetaConfiguration").getDependsOn());
    }

    @Test
    void shouldAppendSchemaInitializerToExistingDependsOn() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("appScaffoldSchemaInitializer", new RootBeanDefinition(AppScaffoldSchemaInitializer.class));

        RootBeanDefinition bootApplication = new RootBeanDefinition(BootApplication.class);
        bootApplication.setDependsOn("existingDependency");
        beanFactory.registerBeanDefinition("customBootApplication", bootApplication);

        new AppScaffoldAutoConfiguration.AppScaffoldSchemaInitializerDependsOnPostProcessor()
                .postProcessBeanFactory(beanFactory);

        Assertions.assertArrayEquals(new String[]{"existingDependency", "appScaffoldSchemaInitializer"},
                beanFactory.getBeanDefinition("customBootApplication").getDependsOn());
    }
}
