package cn.geelato.web.platform.audit.boot;

import cn.geelato.core.orm.event.DeleteEventManager;
import cn.geelato.core.orm.event.SaveEventManager;
import cn.geelato.web.platform.audit.listener.OrmAuditCollector.AuditServices;
import cn.geelato.web.platform.audit.context.AuditContext;
import cn.geelato.web.platform.audit.listener.AuditLogDeleteEventListener;
import cn.geelato.web.platform.audit.listener.AuditLogSaveEventListener;
import cn.geelato.web.platform.audit.service.AuditBusinessNamer;
import cn.geelato.web.platform.audit.service.AuditContextProvider;
import cn.geelato.web.platform.audit.service.AuditDiffService;
import cn.geelato.web.platform.audit.service.AuditLogService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 审计日志自动装配。
 *
 * <p>仅当显式配置 {@code geelato.platform.audit.enabled=true} 时才装配（默认不装配，零开销）：
 * <ol>
 *   <li>构造共享 {@link AuditServices}，注册第2层 ORM 监听器到 {@code SaveEventManager}/{@code DeleteEventManager}；</li>
 *   <li>注册 {@link AuditOperationFilter}（通道A + 请求级上下文生命周期）；</li>
 *   <li>第1层切面 {@code AuditLogAspect} 与各服务由 {@code @Component} 自动扫描注入。</li>
 * </ol>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "geelato.platform.audit", name = "enabled", havingValue = "true", matchIfMissing = false)
public class AuditLogAutoConfiguration {

    private final AuditLogProperties properties;

    /** 持有注册的监听器引用，供容器销毁时注销（B2 生命周期管理，防热部署泄漏）。 */
    private AuditLogSaveEventListener registeredSaveListener;
    private AuditLogDeleteEventListener registeredDeleteListener;

    public AuditLogAutoConfiguration(AuditLogProperties properties) {
        this.properties = properties;
    }

    /**
     * 共享服务集合：把监听器依赖的服务聚合，供两个监听器复用。
     */
    @Bean
    public AuditServices auditServices(AuditLogService auditLogService,
                                       AuditDiffService diffService,
                                       AuditBusinessNamer namer,
                                       AuditContextProvider contextProvider) {
        return new AuditServices(auditLogService, diffService, namer, contextProvider, properties);
    }

    /**
     * 第2层监听器：保存事件。
     */
    @Bean
    public AuditLogSaveEventListener auditLogSaveEventListener(AuditServices auditServices) {
        return new AuditLogSaveEventListener(auditServices);
    }

    /**
     * 第2层监听器：删除事件。
     */
    @Bean
    public AuditLogDeleteEventListener auditLogDeleteEventListener(AuditServices auditServices) {
        return new AuditLogDeleteEventListener(auditServices);
    }

    /**
     * 启动后将监听器注册到 ORM 事件管理器（仅当 enabled）。
     */
    @PostConstruct
    public void registerListeners() {
        // 通过 ApplicationContext 在所有 Bean 就绪后注册，这里先打日志
        log.info("审计日志已启用，将在容器就绪后注册 ORM 事件监听器");
    }

    /**
     * 注册监听器的 Bean：依赖监听器 Bean 自身（确保先创建）。
     */
    @Bean
    public Object auditListenerRegistrar(AuditLogSaveEventListener saveListener,
                                         AuditLogDeleteEventListener deleteListener) {
        this.registeredSaveListener = saveListener;
        this.registeredDeleteListener = deleteListener;
        SaveEventManager.registerBeforeIfAbsent(saveListener);
        DeleteEventManager.registerBeforeIfAbsent(deleteListener);
        log.info("审计 ORM 监听器已注册: save={}, delete={}",
                saveListener.getClass().getSimpleName(), deleteListener.getClass().getSimpleName());
        return new Object();
    }

    /**
     * 通道A 过滤器注册。
     */
    @Bean
    public FilterRegistrationBean<AuditOperationFilter> auditOperationFilterRegistration() {
        FilterRegistrationBean<AuditOperationFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new AuditOperationFilter());
        reg.addUrlPatterns("/*");
        reg.setName("auditOperationFilter");
        // 在 SecurityContextFilter(order=1) 之后执行
        reg.setOrder(3);
        return reg;
    }

    @PreDestroy
    public void shutdown() {
        // B2：容器销毁时注销监听器，避免热部署/上下文刷新时累积泄漏
        try {
            if (registeredSaveListener != null) {
                SaveEventManager.unregisterBefore(registeredSaveListener);
            }
            if (registeredDeleteListener != null) {
                DeleteEventManager.unregisterBefore(registeredDeleteListener);
            }
            log.info("审计 ORM 监听器已注销");
        } catch (Exception ignore) {
        }
    }
}
