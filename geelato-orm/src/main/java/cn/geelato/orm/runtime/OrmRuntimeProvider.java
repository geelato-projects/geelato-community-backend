package cn.geelato.orm.runtime;

import cn.geelato.orm.config.MetaExecutorMode;
import cn.geelato.orm.config.OrmProperties;
import cn.geelato.orm.executor.DefaultMetaCommandExecutor;
import cn.geelato.orm.executor.MetaCommandExecutor;
import cn.geelato.orm.executor.spi.DaoMetaExecutionStrategy;
import cn.geelato.orm.executor.spi.MetaExecutionStrategy;
import org.springframework.context.ApplicationContext;

/**
 * ORM 运行时能力提供者。
 * 统一负责在 DSL/适配器入口解析执行器与默认填充器，降低对静态 Bean 直接查找的耦合。
 */
public class OrmRuntimeProvider {

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

    private MetaExecutionStrategy createExecutionStrategy() {
        return createExecutionStrategy(applicationContext, ormProperties);
    }

    /**
     * 按 {@code geelato.orm.execution-mode} 装配执行策略的唯一入口，
     * 自动配置与 DSL 回退路径共用，避免分支逻辑两处漂移。
     * 新增 {@link MetaExecutorMode} 枚举值时必须在此补齐分支，箭头 switch
     * 表达式在缺少分支时编译失败（穷尽性校验），不会静默回退到 DAO。
     */
    public static MetaExecutionStrategy createExecutionStrategy(ApplicationContext applicationContext, OrmProperties ormProperties) {
        MetaExecutorMode mode = ormProperties == null ? MetaExecutorMode.DAO : ormProperties.getExecutionMode();
        return switch (mode) {
            case DAO -> new DaoMetaExecutionStrategy(OrmDaoResolver.resolve(applicationContext, ormProperties));
        };
    }
}
