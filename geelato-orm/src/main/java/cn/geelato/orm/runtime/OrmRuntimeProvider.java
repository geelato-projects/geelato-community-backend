package cn.geelato.orm.runtime;

import cn.geelato.orm.config.OrmProperties;
import cn.geelato.orm.executor.DefaultMetaCommandExecutor;
import cn.geelato.orm.executor.MetaCommandExecutor;
import cn.geelato.orm.executor.spi.DaoMetaExecutionStrategy;
import cn.geelato.orm.executor.spi.JdbcTemplateMetaExecutionStrategy;
import cn.geelato.orm.executor.spi.MetaExecutionStrategy;
import cn.geelato.orm.fill.DefaultSaveDefaultValueFiller;
import cn.geelato.orm.fill.SaveDefaultValueFiller;
import org.springframework.context.ApplicationContext;

/**
 * ORM 运行时能力提供者。
 * 统一负责在 DSL/适配器入口解析执行器与默认填充器，降低对静态 Bean 直接查找的耦合。
 */
public class OrmRuntimeProvider {

    private static final SaveDefaultValueFiller FALLBACK_FILLER = new DefaultSaveDefaultValueFiller();

    private final ApplicationContext applicationContext;
    private final OrmProperties ormProperties;
    private volatile MetaCommandExecutor cachedMetaCommandExecutor;

    public OrmRuntimeProvider(ApplicationContext applicationContext, OrmProperties ormProperties) {
        this.applicationContext = applicationContext;
        this.ormProperties = ormProperties;
    }

    public MetaCommandExecutor metaCommandExecutor() {
        MetaCommandExecutor beanExecutor = applicationContext.getBeanProvider(MetaCommandExecutor.class).getIfAvailable();
        if (beanExecutor != null) {
            return beanExecutor;
        }

        MetaCommandExecutor local = cachedMetaCommandExecutor;
        if (local != null) {
            return local;
        }

        synchronized (this) {
            if (cachedMetaCommandExecutor == null) {
                MetaExecutionStrategy executionStrategy = applicationContext.getBeanProvider(MetaExecutionStrategy.class).getIfAvailable();
                if (executionStrategy == null) {
                    executionStrategy = createExecutionStrategy();
                }
                cachedMetaCommandExecutor = new DefaultMetaCommandExecutor(executionStrategy);
            }
            return cachedMetaCommandExecutor;
        }
    }

    public SaveDefaultValueFiller saveDefaultValueFiller() {
        SaveDefaultValueFiller filler = applicationContext.getBeanProvider(SaveDefaultValueFiller.class).getIfAvailable();
        return filler != null ? filler : FALLBACK_FILLER;
    }

    private MetaExecutionStrategy createExecutionStrategy() {
        if (ormProperties != null && ormProperties.getExecutionMode() == cn.geelato.orm.config.MetaExecutorMode.JDBC_TEMPLATE) {
            return new JdbcTemplateMetaExecutionStrategy(OrmJdbcTemplateResolver.resolve(applicationContext, ormProperties));
        }
        return new DaoMetaExecutionStrategy(OrmDaoResolver.resolve(applicationContext, ormProperties));
    }
}
