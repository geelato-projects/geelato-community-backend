package cn.geelato.archive.engine.boot;

import cn.geelato.archive.boot.ArchivePolicyAutoConfiguration;
import cn.geelato.archive.config.ArchiveProperties;
import cn.geelato.archive.service.ArchivePolicyService;
import cn.geelato.archive.engine.trigger.ArchiveScheduler;
import cn.geelato.archive.engine.trigger.ArchiveTrigger;
import cn.geelato.datasource.DynamicDataSourceRegistry;
import cn.geelato.datasource.EntityDataSourceResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 归档引擎自动装配：建表 → 预置模板装载 → 调度器启动。
 * <p>业务组件（channel/engine/trigger/controller，@Component）由宿主
 * {@code @ComponentScan("cn.geelato")} 发现（对齐 geelato-mail 模式）；
 * 本类装配基础设施 Bean，均带 {@code @ConditionalOnMissingBean} 让位宿主自定义。
 * 宿主引入 geelato-archive-engine 一条依赖即全部生效（quickstart 择机引入）。</p>
 */
@AutoConfiguration
@AutoConfigureAfter(ArchivePolicyAutoConfiguration.class)
@Slf4j
public class ArchiveEngineAutoConfiguration {

    /**
     * 启动时幂等建表（geelato.archive.auto-init-tables，默认 true），
     * 表建在归档实体路由的数据源（@Entity(catalog="platform")，缺省主库）。
     */
    @Bean
    @ConditionalOnBean({EntityDataSourceResolver.class, DynamicDataSourceRegistry.class})
    @ConditionalOnMissingBean(ArchiveSchemaInitializer.class)
    @ConditionalOnProperty(prefix = "geelato.archive", name = "auto-init-tables", havingValue = "true", matchIfMissing = true)
    public ArchiveSchemaInitializer archiveSchemaInitializer(EntityDataSourceResolver entityDataSourceResolver,
                                                             DynamicDataSourceRegistry dynamicDataSourceRegistry) {
        return new ArchiveSchemaInitializer(entityDataSourceResolver, dynamicDataSourceRegistry);
    }

    /** 建表完成后装载预置策略模板（幂等，geelato.archive.auto-load-presets 默认 true）；
     *  定时调度默认不启动（scheduler-enabled 默认 false，引擎日常静默，需要时再 run 起来） */
    @Bean
    @ConditionalOnBean(ArchiveSchemaInitializer.class)
    @ConditionalOnMissingBean(name = "archiveBootstrap")
    public InitializingBean archiveBootstrap(ArchiveSchemaInitializer schemaInitializer,
                                             ArchivePolicyService policyService,
                                             ArchiveProperties properties,
                                             org.springframework.beans.factory.ObjectProvider<ArchiveScheduler> schedulerProvider) {
        return () -> {
            schemaInitializer.afterPropertiesSet();
            if (properties.isAutoLoadPresets()) {
                int created = policyService.loadPresets();
                if (created > 0) {
                    log.info("归档预置模板装载完成：新增 {} 条（默认停用）", created);
                }
            }
            ArchiveScheduler scheduler = schedulerProvider.getIfAvailable();
            if (scheduler == null) {
                return;
            }
            if (properties.isSchedulerEnabled()) {
                scheduler.start();
            } else {
                log.info("归档调度器默认关闭（引擎日常静默）。按需执行：POST /archive/run/trigger/{{policyId}} 单策略、"
                        + "POST /archive/run/triggerAll 全部启用策略一次、POST /archive/scheduler/start 启动每日定时；"
                        + "随应用自动启动请配置 geelato.archive.scheduler-enabled=true");
            }
        };
    }

    /**
     * 每日调度器（默认不启动，仅空对象零资源；start/stop 运行时可控，
     * 或 scheduler-enabled=true 随应用启动；宿主自定义时让位）。
     */
    @Bean
    @ConditionalOnBean(ArchiveTrigger.class)
    @ConditionalOnMissingBean(ArchiveScheduler.class)
    public ArchiveScheduler archiveScheduler(ArchiveTrigger trigger, ArchiveProperties properties) {
        return new ArchiveScheduler(trigger, properties);
    }
}
