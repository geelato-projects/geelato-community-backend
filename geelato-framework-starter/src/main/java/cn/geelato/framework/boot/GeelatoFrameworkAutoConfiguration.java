package cn.geelato.framework.boot;

import cn.geelato.datasource.DataSourceFactory;
import cn.geelato.datasource.DbHostMapFileLoader;
import cn.geelato.datasource.DynamicDaoConfiguration;
import cn.geelato.datasource.DynamicDaoFieldProcessor;
import cn.geelato.datasource.DynamicDataSourceConfiguration;
import cn.geelato.datasource.DynamicDataSourceProperties;
import cn.geelato.datasource.DynamicDataSourceRegistry;
import cn.geelato.datasource.EntityDataSourceResolver;
import cn.geelato.datasource.interceptor.DataSourceInterceptor;
import cn.geelato.datasource.transaction.TransactionConfig;
import cn.geelato.web.common.filter.FilterConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({
        FilterConfiguration.class,
        FrameworkJdbcAutoConfiguration.class,
        GeelatoFrameworkAutoConfiguration.DynamicDataSourceSupportAutoConfiguration.class
})
public class GeelatoFrameworkAutoConfiguration {

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "spring.datasource.primary", name = "jdbc-url")
    @Import({
            DynamicDataSourceConfiguration.class,
            DynamicDaoConfiguration.class,
            DynamicDataSourceProperties.class,
            DynamicDataSourceRegistry.class,
            DataSourceFactory.class,
            DbHostMapFileLoader.class,
            DynamicDaoFieldProcessor.class,
            EntityDataSourceResolver.class,
            DataSourceInterceptor.class,
            TransactionConfig.class
    })
    static class DynamicDataSourceSupportAutoConfiguration {
    }
}
