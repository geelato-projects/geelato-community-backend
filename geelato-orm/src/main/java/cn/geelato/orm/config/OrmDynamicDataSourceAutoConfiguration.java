package cn.geelato.orm.config;

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
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnBean(name = "primaryJdbcTemplate")
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
public class OrmDynamicDataSourceAutoConfiguration {
}

