package cn.geelato.orm.config;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.util.BeansUtils;
import cn.geelato.lang.meta.Entity;
import cn.geelato.orm.executor.DefaultMetaCommandExecutor;
import cn.geelato.orm.executor.MetaCommandExecutor;
import cn.geelato.orm.fill.DefaultSaveDefaultValueFiller;
import cn.geelato.orm.fill.SaveDefaultValueFiller;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.StringUtils;
import org.springframework.util.ClassUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Configuration
@EnableConfigurationProperties(OrmProperties.class)
public class OrmAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public BeansUtils beansUtils() {
        return new BeansUtils();
    }

    @Bean
    @ConditionalOnBean(Dao.class)
    @ConditionalOnMissingBean
    public MetaCommandExecutor metaCommandExecutor(ApplicationContext applicationContext, OrmProperties ormProperties) {
        return new DefaultMetaCommandExecutor(resolveDao(applicationContext, ormProperties));
    }

    @Bean
    @ConditionalOnMissingBean
    public SaveDefaultValueFiller saveDefaultValueFiller() {
        return new DefaultSaveDefaultValueFiller();
    }

    @Bean
    @ConditionalOnMissingBean(name = "ormEntityMetadataInitializer")
    public SmartInitializingSingleton ormEntityMetadataInitializer(ApplicationContext applicationContext, OrmProperties ormProperties) {
        return () -> scanAndParseEntities(applicationContext, ormProperties);
    }

    private void scanAndParseEntities(ApplicationContext applicationContext, OrmProperties ormProperties) {
        if (ormProperties != null && Boolean.FALSE.equals(ormProperties.getEntityAutoScanEnabled())) {
            return;
        }

        List<String> basePackages = resolveEntityScanBasePackages(applicationContext, ormProperties);
        if (basePackages.isEmpty()) {
            return;
        }

        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.setEnvironment(applicationContext.getEnvironment());
        scanner.setResourceLoader(applicationContext);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        for (String basePackage : basePackages) {
            for (BeanDefinition beanDefinition : scanner.findCandidateComponents(basePackage)) {
                String className = beanDefinition.getBeanClassName();
                if (!StringUtils.hasText(className)) {
                    continue;
                }
                try {
                    Class<?> entityClass = ClassUtils.forName(className, applicationContext.getClassLoader());
                    MetaManager.singleInstance().parseOne(entityClass);
                } catch (ClassNotFoundException ex) {
                    throw new IllegalStateException("Failed to load @Entity class: " + className, ex);
                }
            }
        }
    }

    private List<String> resolveEntityScanBasePackages(ApplicationContext applicationContext, OrmProperties ormProperties) {
        if (ormProperties != null && ormProperties.getEntityScanBasePackages() != null && ormProperties.getEntityScanBasePackages().length > 0) {
            return Arrays.stream(ormProperties.getEntityScanBasePackages())
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toList());
        }
        try {
            return AutoConfigurationPackages.get(applicationContext);
        } catch (IllegalStateException ex) {
            return List.of();
        }
    }

    private Dao resolveDao(ApplicationContext applicationContext, OrmProperties ormProperties) {
        if (ormProperties != null && StringUtils.hasText(ormProperties.getDaoBeanName())) {
            return resolveConfiguredDao(applicationContext, ormProperties.getDaoBeanName());
        }

        try {
            return applicationContext.getBean(Dao.class);
        } catch (NoUniqueBeanDefinitionException ex) {
            return resolveCompatibilityDao(applicationContext, ex);
        } catch (NoSuchBeanDefinitionException ex) {
            throw new IllegalStateException("No Dao bean found for MetaCommandExecutor", ex);
        }
    }

    private Dao resolveConfiguredDao(ApplicationContext applicationContext, String daoBeanName) {
        if (!applicationContext.containsBean(daoBeanName)) {
            throw new IllegalStateException("Configured Dao bean not found: " + daoBeanName);
        }
        return applicationContext.getBean(daoBeanName, Dao.class);
    }

    private Dao resolveCompatibilityDao(ApplicationContext applicationContext, NoUniqueBeanDefinitionException ex) {
        Map<String, Dao> daoBeans = applicationContext.getBeansOfType(Dao.class);
        if (daoBeans.size() == 1) {
            return daoBeans.values().iterator().next();
        }
        if (daoBeans.containsKey("dynamicDao")) {
            return daoBeans.get("dynamicDao");
        }
        if (daoBeans.containsKey("primaryDao")) {
            return daoBeans.get("primaryDao");
        }
        throw new IllegalStateException(buildAmbiguousDaoMessage(daoBeans), ex);
    }

    private String buildAmbiguousDaoMessage(Map<String, Dao> daoBeans) {
        StringJoiner joiner = new StringJoiner(", ");
        for (String beanName : daoBeans.keySet()) {
            joiner.add(beanName);
        }
        return "Multiple Dao beans found for MetaCommandExecutor: [" + joiner + "], please configure geelato.orm.dao-bean-name";
    }
}
