package cn.geelato.orm.config;

import cn.geelato.core.orm.Dao;
import cn.geelato.orm.executor.DefaultMetaCommandExecutor;
import cn.geelato.orm.executor.MetaCommandExecutor;
import cn.geelato.orm.fill.DefaultSaveDefaultValueFiller;
import cn.geelato.orm.fill.SaveDefaultValueFiller;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrmAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MetaCommandExecutor metaCommandExecutor(ApplicationContext applicationContext) {
        Dao dao = applicationContext.containsBean("dynamicDao")
                ? applicationContext.getBean("dynamicDao", Dao.class)
                : applicationContext.getBean(Dao.class);
        return new DefaultMetaCommandExecutor(dao);
    }

    @Bean
    @ConditionalOnMissingBean
    public SaveDefaultValueFiller saveDefaultValueFiller() {
        return new DefaultSaveDefaultValueFiller();
    }
}
