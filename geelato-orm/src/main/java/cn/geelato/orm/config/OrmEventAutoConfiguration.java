package cn.geelato.orm.config;

import cn.geelato.core.orm.event.DeleteEventManager;
import cn.geelato.core.orm.event.EventExecutorFactory;
import cn.geelato.core.orm.event.SaveEventManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;

/**
 * ORM 事件机制自动装配。
 *
 * <p>A4：启动时据 {@link OrmEventProperties} 构造 save/delete 事件线程池（有界队列 + CallerRunsPolicy），
 * 通过 {@code setExecutor} 注入管理器（替换默认池并优雅关闭旧池）。
 *
 * <p>B2：容器销毁时调用 {@code SaveEventManager.shutdown()} / {@code DeleteEventManager.shutdown()}，
 * 优雅关闭线程池，避免线程泄漏（尤其热部署/上下文刷新场景）。
 *
 * <p>可通过 {@code geelato.orm.event.enabled=false} 关闭本装配（回退到管理器默认池）。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(OrmEventProperties.class)
@ConditionalOnProperty(prefix = "geelato.orm.event", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrmEventAutoConfiguration {

    private final OrmEventProperties properties;

    public OrmEventAutoConfiguration(OrmEventProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void applyExecutors() {
        OrmEventProperties.Pool save = properties.getSave();
        OrmEventProperties.Pool del = properties.getDelete();
        ExecutorService savePool = EventExecutorFactory.create("save-event-",
                save.getPoolSize(), save.getQueueCapacity());
        ExecutorService delPool = EventExecutorFactory.create("delete-event-",
                del.getPoolSize(), del.getQueueCapacity());
        SaveEventManager.setExecutor(savePool);
        DeleteEventManager.setExecutor(delPool);
        log.info("ORM 事件线程池已装配: save(poolSize={}, queue={}), delete(poolSize={}, queue={})",
                save.getPoolSize(), save.getQueueCapacity(), del.getPoolSize(), del.getQueueCapacity());
    }

    @PreDestroy
    public void shutdown() {
        log.info("ORM 事件线程池关闭中...");
        SaveEventManager.shutdown();
        DeleteEventManager.shutdown();
    }
}
