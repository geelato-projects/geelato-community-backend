package cn.geelato.exception.boot;

import cn.geelato.core.ds.DataSourceManager;
import cn.geelato.core.orm.Dao;
import cn.geelato.exception.engine.kit.TargetService;
import cn.geelato.exception.engine.core.PipelineEngine;
import cn.geelato.exception.engine.hit.HitSubmitService;
import cn.geelato.exception.engine.lifecycle.InstanceLifecycleService;
import cn.geelato.exception.engine.participant.ParticipantResolvers;
import cn.geelato.exception.engine.participant.ParticipantSolver;
import cn.geelato.exception.engine.scan.ExceptionScanScheduler;
import cn.geelato.exception.engine.scan.ScanRunner;
import cn.geelato.script.boot.ScriptEngineCustomizer;
import cn.geelato.script.core.ScriptEngine;
import cn.geelato.script.core.ScriptProfile;
import cn.geelato.security.SecurityProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.util.Set;

/**
 * 异常引擎装配：enabled 缺省 false，关闭时不装配任何引擎 Bean（trialRun/adminScan/entityEvent 维持占位行为）。
 * exception-judge profile 不限定命名空间（注册到的注解 kit——SDK 内置与消费方扫描/注册的——全部可见），
 * 仅收紧超时预算；结构性禁取数仅告警渲染场景（alert-render profile）。
 */
@AutoConfiguration
@ConditionalOnClass(ScriptEngine.class)
@EnableConfigurationProperties(ExceptionEngineProperties.class)
public class ExceptionEngineAutoConfiguration {

    @Bean
    public ScriptEngineCustomizer exceptionScriptEngineCustomizer(ExceptionEngineProperties properties,
                                                                   ApplicationContext appCtx,
                                                                   ObjectProvider<cn.geelato.script.kit.sql.ConnectPoolProvider> poolProvider) {
        return builder -> builder
                .profile(ScriptProfile.builder()
                        .id(PipelineEngine.PROFILE_ID)
                        .namespaces(Set.of(ScriptProfile.ALL))
                        .variables(Set.of(ScriptProfile.ALL))
                        .defaultTimeoutMs(properties.getPipeline().getStepTimeoutMs())
                        .build())
                .dataSourceSupplier(() -> resolveDataSource(properties, appCtx))
                .connectPoolProvider(poolProvider.getIfAvailable(cn.geelato.script.kit.sql.ConnectPoolProvider::defaultProvider))
                .httpEnabled(properties.getHttp().isEnabled())
                .httpAllowlist(properties.getHttp().getAllowlist())
                .httpTimeoutMs(properties.getHttp().getTimeoutMs())
                .registerScoped("target", TargetService.class, ctx -> new TargetService(
                        new org.springframework.jdbc.core.JdbcTemplate(resolveDataSource(properties, appCtx)), ctx));
    }

    /**
     * 联邦查询取池：动态连接走 orm Registry（懒建池），primary/默认回退宿主数据源定位链，
     * 其余再回退 core DataSourceManager。
     */
    @Bean
    @ConditionalOnMissingBean
    public cn.geelato.script.kit.sql.ConnectPoolProvider exceptionConnectPoolProvider(
            ExceptionEngineProperties properties, ApplicationContext appCtx,
            ObjectProvider<cn.geelato.datasource.DynamicDataSourceRegistry> registryProvider) {
        return connectId -> {
            if (connectId == null || connectId.isBlank()
                    || "primary".equalsIgnoreCase(connectId) || "local".equalsIgnoreCase(connectId)) {
                return resolveDataSource(properties, appCtx);
            }
            cn.geelato.datasource.DynamicDataSourceRegistry registry = registryProvider.getIfAvailable();
            if (registry != null && registry.containsDataSource(connectId)) {
                return registry.getDataSource(connectId);
            }
            DataSourceManager manager = DataSourceManager.singleInstance();
            return manager.getRegisteredDataSource(connectId);
        };
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "geelato.exception.engine", name = "enabled", havingValue = "true")
    public PipelineEngine exceptionPipelineEngine(ObjectProvider<ScriptEngine> scriptEngine,
                                                  ExceptionEngineProperties properties) {
        return new PipelineEngine(scriptEngine.getObject(),
                properties.getPipeline().getStepTimeoutMs(),
                properties.getPipeline().getPipelineTimeoutMs(),
                properties.getPipeline().getTrialTimeoutMs(),
                properties.getPipeline().getStepOutputMaxBytes());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "geelato.exception.engine", name = "enabled", havingValue = "true")
    public ParticipantSolver exceptionParticipantSolver(ObjectProvider<SecurityProvider> securityProvider) {
        return new ParticipantSolver(ParticipantResolvers.defaults(securityProvider.getIfAvailable()));
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "geelato.exception.engine", name = "enabled", havingValue = "true")
    public InstanceLifecycleService exceptionInstanceLifecycleService(
            ObjectProvider<ApplicationEventPublisher> publisher) {
        return new InstanceLifecycleService(publisher);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "geelato.exception.engine", name = "enabled", havingValue = "true")
    public HitSubmitService exceptionHitSubmitService(@Qualifier("dynamicDao") Dao dynamicDao,
                                                      ParticipantSolver participantSolver,
                                                      InstanceLifecycleService lifecycle) {
        return new HitSubmitService(dynamicDao, participantSolver, lifecycle);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "geelato.exception.engine", name = "enabled", havingValue = "true")
    public ScanRunner exceptionScanRunner(@Qualifier("dynamicDao") Dao dynamicDao,
                                          PipelineEngine pipelineEngine,
                                          HitSubmitService hitSubmitService,
                                          InstanceLifecycleService lifecycle,
                                          ExceptionEngineProperties properties) {
        return new ScanRunner(dynamicDao, pipelineEngine, hitSubmitService, lifecycle,
                properties.getScan().getLockStaleMinutes(), properties.getScan().getThreadPoolSize());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "geelato.exception.engine", name = "enabled", havingValue = "true")
    public ExceptionScanScheduler exceptionScanScheduler(@Qualifier("dynamicDao") Dao dynamicDao,
                                                         ScanRunner scanRunner,
                                                         ExceptionEngineProperties properties) {
        return new ExceptionScanScheduler(dynamicDao, scanRunner, properties.getScan().getTickSeconds());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "geelato.exception.engine", name = "enabled", havingValue = "true")
    public cn.geelato.exception.engine.trigger.EntityEventReactor exceptionEntityEventReactor(
            @Qualifier("dynamicDao") Dao dynamicDao,
            PipelineEngine pipelineEngine,
            HitSubmitService hitSubmitService,
            InstanceLifecycleService lifecycle) {
        return new cn.geelato.exception.engine.trigger.EntityEventReactor(dynamicDao, pipelineEngine, hitSubmitService, lifecycle);
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "geelato.exception.engine", name = "enabled", havingValue = "true")
    public cn.geelato.exception.engine.trigger.ChangeEventDispatcher exceptionChangeEventDispatcher(
            cn.geelato.exception.engine.trigger.EntityEventReactor reactor,
            ExceptionEngineProperties properties) {
        return new cn.geelato.exception.engine.trigger.ChangeEventDispatcher(reactor, properties.getTrigger().getQueueCapacity());
    }


    /**
     * 业务库数据源定位：优先经平台 DataSourceManager 按 connectId（数据源 key，非 Bean 名）解析，
     * 未命中则回退默认 key；独立/精简宿主未走 DataSourceManager 注册时，兜底按 Bean 名/类型取容器内 DataSource。
     */
    public static DataSource resolveDataSource(ExceptionEngineProperties properties, ApplicationContext appCtx) {
        String connectId = properties.getSql().getConnectId();
        DataSource candidate = null;
        try {
            DataSourceManager manager = DataSourceManager.singleInstance();
            candidate = manager.getRegisteredDataSource(connectId);
            if (candidate == null) {
                candidate = manager.getRegisteredDataSource(manager.getDefaultDataSourceKey());
            }
        } catch (Exception ignored) {
        }
        if (candidate == null) {
            try {
                candidate = appCtx.getBean(connectId, DataSource.class);
            } catch (Exception ignored) {
            }
        }
        if (candidate == null) {
            try {
                candidate = appCtx.getBean(DataSource.class);
            } catch (Exception e) {
                throw new IllegalStateException("异常引擎数据源解析失败: connectId=" + connectId, e);
            }
        }
        return candidate;
    }
}
