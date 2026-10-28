package cn.geelato.alert.boot;

import cn.geelato.alert.engine.AlertEngineScheduler;
import cn.geelato.alert.engine.FlushScanner;
import cn.geelato.alert.engine.RepeatScanner;
import cn.geelato.alert.engine.ScheduleRunner;
import cn.geelato.alert.engine.dataset.DatasetExecutor;
import cn.geelato.alert.engine.deliver.DeliverEngine;
import cn.geelato.alert.engine.deliver.MessageTransport;
import cn.geelato.alert.engine.deliver.OkHttpMessageTransport;
import cn.geelato.alert.engine.event.AlertGrouper;
import cn.geelato.alert.engine.event.AlertMatcher;
import cn.geelato.alert.engine.event.ResolveHandler;
import cn.geelato.alert.engine.scan.AlertSourceScanner;
import cn.geelato.alert.engine.receiver.AlertReceiverSolver;
import cn.geelato.alert.engine.receiver.ReceiverSelectorResolver;
import cn.geelato.alert.engine.receiver.ReceiverSelectors;
import cn.geelato.alert.engine.render.AlertRenderer;
import cn.geelato.core.orm.Dao;
import cn.geelato.script.core.ScriptEngine;
import cn.geelato.security.SecurityProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 告警引擎装配（匹配/聚合/冲刷/渲染/投递全链路），宿主显式开启
 * （geelato.alert.engine.enabled=true，缺省关——对齐 geelato.exception.engine 惯例）。
 * 渲染为必经环节：ScriptEngine 不可用时启动快速失败。
 */
@AutoConfiguration
@ConditionalOnClass(cn.geelato.script.core.ScriptEngine.class)
@ConditionalOnProperty(value = "geelato.alert.engine.enabled", havingValue = "true")
@EnableConfigurationProperties(AlertEngineProperties.class)
public class AlertEngineAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MessageTransport alertMessageTransport(AlertEngineProperties properties) {
        return new OkHttpMessageTransport(properties.getMessageBaseUrl(), properties.getMessageTimeoutMs(),
                properties.getMessageSystemCode(), properties.getMessageApiKey());
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertRenderer alertRenderer(Dao dynamicDao, ObjectProvider<ScriptEngine> scriptEngineProvider) {
        ScriptEngine engine = scriptEngineProvider.getIfAvailable();
        if (engine == null) {
            throw new IllegalStateException("geelato-script ScriptEngine 不可用：渲染是告警引擎必经环节，"
                    + "请引入 geelato-script 并确认脚本引擎装配");
        }
        return new AlertRenderer(dynamicDao, engine);
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertReceiverSolver alertReceiverSolver(ObjectProvider<SecurityProvider> securityProvider,
                                                   AlertEngineProperties properties) {
        java.util.List<ReceiverSelectorResolver> resolvers =
                ReceiverSelectors.defaults(securityProvider.getIfAvailable());
        return new AlertReceiverSolver(resolvers, securityProvider.getIfAvailable(), properties.getFallbackRoleCode());
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertMatcher alertMatcher(Dao dynamicDao) {
        return new AlertMatcher(dynamicDao);
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertGrouper alertGrouper(Dao dynamicDao) {
        return new AlertGrouper(dynamicDao);
    }

    @Bean
    @ConditionalOnMissingBean
    public ResolveHandler alertResolveHandler(Dao dynamicDao, AlertGrouper grouper) {
        return new ResolveHandler(dynamicDao, grouper);
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertSourceScanner alertSourceScanner(Dao dynamicDao, AlertMatcher matcher, AlertGrouper grouper,
                                                 ResolveHandler resolveHandler, AlertEngineProperties properties) {
        return new AlertSourceScanner(dynamicDao, matcher, grouper, resolveHandler, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public DatasetExecutor alertDatasetExecutor(Dao dynamicDao) {
        return new DatasetExecutor(dynamicDao);
    }

    @Bean
    @ConditionalOnMissingBean
    public FlushScanner alertFlushScanner(Dao dynamicDao, AlertRenderer renderer, AlertEngineProperties properties) {
        return new FlushScanner(dynamicDao, renderer, properties.getDeliverBatchSize(),
                properties.getMaxRetry(), properties.getBackoffBaseSeconds());
    }

    @Bean
    @ConditionalOnMissingBean
    public RepeatScanner alertRepeatScanner(Dao dynamicDao, AlertGrouper grouper) {
        return new RepeatScanner(dynamicDao, grouper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ScheduleRunner alertScheduleRunner(Dao dynamicDao, DatasetExecutor datasetExecutor) {
        return new ScheduleRunner(dynamicDao, datasetExecutor);
    }

    @Bean
    @ConditionalOnMissingBean
    public DeliverEngine alertDeliverEngine(Dao dynamicDao, MessageTransport transport, AlertRenderer renderer,
                                            AlertReceiverSolver receiverSolver, AlertEngineProperties properties) {
        return new DeliverEngine(dynamicDao, transport, renderer, receiverSolver, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertEngineScheduler alertEngineScheduler(AlertSourceScanner sourceScanner, FlushScanner flushScanner,
                                                     DeliverEngine deliverEngine, RepeatScanner repeatScanner,
                                                     ScheduleRunner scheduleRunner, AlertEngineProperties properties) {
        return new AlertEngineScheduler(sourceScanner, flushScanner, deliverEngine, repeatScanner, scheduleRunner, properties);
    }
}
