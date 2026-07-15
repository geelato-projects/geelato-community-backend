package cn.geelato.orm.config;

import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.util.BeansUtils;
import cn.geelato.lang.meta.Entity;
import cn.geelato.orm.executor.DefaultMetaCommandExecutor;
import cn.geelato.orm.executor.MetaCommandExecutor;
import cn.geelato.orm.executor.spi.DaoMetaExecutionStrategy;
import cn.geelato.orm.executor.spi.JdbcTemplateMetaExecutionStrategy;
import cn.geelato.orm.executor.spi.MetaExecutionStrategy;
import cn.geelato.orm.fill.DefaultSaveDefaultValueFiller;
import cn.geelato.orm.fill.SaveDefaultValueFiller;
import cn.geelato.orm.runtime.OrmDaoResolver;
import cn.geelato.orm.runtime.OrmJdbcTemplateResolver;
import cn.geelato.orm.runtime.OrmRuntimeProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.StringUtils;
import org.springframework.util.ClassUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
    @ConditionalOnMissingBean
    public OrmRuntimeProvider ormRuntimeProvider(ApplicationContext applicationContext, OrmProperties ormProperties) {
        return new OrmRuntimeProvider(applicationContext, ormProperties);
    }

    @Bean
    @ConditionalOnMissingBean(value = {MetaExecutionStrategy.class, MetaCommandExecutor.class})
    public MetaExecutionStrategy metaExecutionStrategy(ApplicationContext applicationContext, OrmProperties ormProperties) {
        if (ormProperties != null && ormProperties.getExecutionMode() == MetaExecutorMode.JDBC_TEMPLATE) {
            return new JdbcTemplateMetaExecutionStrategy(OrmJdbcTemplateResolver.resolve(applicationContext, ormProperties));
        }
        return new DaoMetaExecutionStrategy(OrmDaoResolver.resolve(applicationContext, ormProperties));
    }

    @Bean
    @ConditionalOnMissingBean(MetaCommandExecutor.class)
    public MetaCommandExecutor metaCommandExecutor(MetaExecutionStrategy metaExecutionStrategy) {
        return new DefaultMetaCommandExecutor(metaExecutionStrategy);
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

    @Bean
    @ConditionalOnMissingBean(name = "ormDefaultDataSourceInitializer")
    public SmartInitializingSingleton ormDefaultDataSourceInitializer(ApplicationContext applicationContext, OrmProperties ormProperties) {
        return () -> {
            String defaultDataSourceKey = resolveEffectiveDefaultDataSourceKey(applicationContext, ormProperties);
            DataSourceManager manager = DataSourceManager.singleInstance();
            manager.setDefaultDataSourceKey(defaultDataSourceKey);
            DataSource dataSource = resolveDefaultDataSource(applicationContext, defaultDataSourceKey);
            if (dataSource != null) {
                manager.registerDataSource(defaultDataSourceKey, dataSource);
            }
        };
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

    private String resolveEffectiveDefaultDataSourceKey(ApplicationContext applicationContext, OrmProperties ormProperties) {
        String configuredDefaultKey = ormProperties == null ? null : ormProperties.getDefaultDataSourceKey();
        if (StringUtils.hasText(configuredDefaultKey)) {
            return configuredDefaultKey;
        }
        if (applicationContext.containsBean("primaryDao")
                || applicationContext.containsBean("primaryJdbcTemplate")
                || applicationContext.containsBean("primaryDataSource")) {
            return "primary";
        }
        return null;
    }

    private DataSource resolveDefaultDataSource(ApplicationContext applicationContext, String defaultDataSourceKey) {
        if (!StringUtils.hasText(defaultDataSourceKey)) {
            return null;
        }
        DataSource directDataSource = getBeanIfPresent(applicationContext, defaultDataSourceKey, DataSource.class);
        if (directDataSource != null) {
            return directDataSource;
        }
        DataSource namedDataSource = getBeanIfPresent(applicationContext, defaultDataSourceKey + "DataSource", DataSource.class);
        if (namedDataSource != null) {
            return namedDataSource;
        }
        JdbcTemplate directJdbcTemplate = getBeanIfPresent(applicationContext, defaultDataSourceKey, JdbcTemplate.class);
        if (directJdbcTemplate != null) {
            return directJdbcTemplate.getDataSource();
        }
        JdbcTemplate namedJdbcTemplate = getBeanIfPresent(applicationContext, defaultDataSourceKey + "JdbcTemplate", JdbcTemplate.class);
        if (namedJdbcTemplate != null) {
            return namedJdbcTemplate.getDataSource();
        }
        Dao directDao = getBeanIfPresent(applicationContext, defaultDataSourceKey, Dao.class);
        if (directDao != null && directDao.getJdbcTemplate() != null) {
            return directDao.getJdbcTemplate().getDataSource();
        }
        Dao namedDao = getBeanIfPresent(applicationContext, defaultDataSourceKey + "Dao", Dao.class);
        if (namedDao != null && namedDao.getJdbcTemplate() != null) {
            return namedDao.getJdbcTemplate().getDataSource();
        }
        Map<String, DataSource> dataSources = applicationContext.getBeansOfType(DataSource.class);
        if (dataSources.size() == 1) {
            return dataSources.values().iterator().next();
        }
        return null;
    }

    private <T> T getBeanIfPresent(ApplicationContext applicationContext, String beanName, Class<T> beanType) {
        if (!applicationContext.containsBean(beanName)) {
            return null;
        }
        return applicationContext.getBean(beanName, beanType);
    }
}
