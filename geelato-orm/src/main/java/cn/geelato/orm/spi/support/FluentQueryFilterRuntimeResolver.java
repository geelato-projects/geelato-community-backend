package cn.geelato.orm.spi.support;

import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.util.BeansUtils;
import cn.geelato.orm.query.MetaQuery;
import cn.geelato.orm.spi.FluentQueryFilterInjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public final class FluentQueryFilterRuntimeResolver {
    private static final Logger log = LoggerFactory.getLogger(FluentQueryFilterRuntimeResolver.class);

    /**
     * 注入器 bean 每个ApplicationContext 实例只解析一次（无热部署注册场景）。
     * 缓存绑定容器实例身份：容器不变则永久复用，容器更换（如单测逐用例换 mock 容器）自动重解析。
     * 上下文未就绪（如无容器的单测环境）每次直查且不缓存，避免把"未找到"空结果永久化。
     * 仅缓存 Spring 装配事实；isEnabled() 筛选（"至多一个启用"判定）保持逐次进行。
     */
    private static volatile BeansSnapshot<FluentQueryFilterInjector> cachedSnapshot;

    private static final class BeansSnapshot<T> {
        final org.springframework.context.ApplicationContext context;
        final Map<String, T> beans;

        BeansSnapshot(org.springframework.context.ApplicationContext context, Map<String, T> beans) {
            this.context = context;
            this.beans = beans;
        }
    }

    private FluentQueryFilterRuntimeResolver() {
    }

    public static void injectIfAvailable(QueryCommand command, MetaQuery query) {
        Entry<String, FluentQueryFilterInjector> injectorEntry = resolveUniqueInjectorEntry(query);
        if (injectorEntry == null) {
            return;
        }
        FluentQueryFilterInjector injector = injectorEntry.getValue();
        String beanName = injectorEntry.getKey();
        String entityName = entityNameOf(query);
        String beanClass = injector.getClass().getName();
        if (!injector.isEnabled()) {
            log.debug("Skip Fluent query filter injection because injector is disabled. entityName={}, beanName={}, beanClass={}",
                    entityName, beanName, beanClass);
            return;
        }
        // 本次查询要求跳过注入过滤：仅当注入器未强制时才跳过
        if (query != null && query.isDisableInjectFilter()) {
            if (!injector.isForceInject()) {
                log.debug("Skip Fluent query filter injection because disableInjectFilter is set. entityName={}, beanName={}",
                        entityName, beanName);
                return;
            }
            log.debug("disableInjectFilter is set but injector is forceInject; still applying. entityName={}, beanName={}",
                    entityName, beanName);
        }
        log.debug("Applying Fluent query filter injection. entityName={}, beanName={}, beanClass={}", entityName, beanName, beanClass);
        injector.inject(command, query);
        log.debug("Completed Fluent query filter injection. entityName={}, beanName={}", entityName, beanName);
    }

    static FluentQueryFilterInjector resolveUniqueInjector() {
        Entry<String, FluentQueryFilterInjector> injectorEntry = resolveUniqueInjectorEntry(null);
        return injectorEntry == null ? null : injectorEntry.getValue();
    }

    static Entry<String, FluentQueryFilterInjector> resolveUniqueInjectorEntry(MetaQuery query) {
        String entityName = entityNameOf(query);
        Map<String, FluentQueryFilterInjector> beans = resolveBeansOnce();
        if (beans.isEmpty()) {
            log.debug("No FluentQueryFilterInjector bean found. Skip Fluent query filter injection. entityName={}", entityName);
            return null;
        }
        List<Entry<String, FluentQueryFilterInjector>> enabledEntries = beans.entrySet().stream()
                .filter(entry -> entry.getValue().isEnabled())
                .toList();
        if (enabledEntries.isEmpty()) {
            log.debug("No enabled FluentQueryFilterInjector among {} bean(s). Skip Fluent query filter injection. entityName={}, beanNames={}",
                    beans.size(), entityName, beans.keySet());
            return null;
        }
        if (enabledEntries.size() > 1) {
            List<String> enabledNames = enabledEntries.stream().map(Entry::getKey).collect(Collectors.toList());
            throw new IllegalStateException("Multiple enabled FluentQueryFilterInjector beans found: " + enabledNames + ". Expected at most 1 enabled.");
        }
        return enabledEntries.get(0);
    }

    private static String entityNameOf(MetaQuery query) {
        return query == null ? null : query.resolveEntityName();
    }

    private static Map<String, FluentQueryFilterInjector> resolveBeansOnce() {
        org.springframework.context.ApplicationContext context = BeansUtils.getApplicationContext();
        if (context == null) {
            return BeansUtils.getBeansOfType(FluentQueryFilterInjector.class);
        }
        BeansSnapshot<FluentQueryFilterInjector> snapshot = cachedSnapshot;
        if (snapshot != null && snapshot.context == context) {
            return snapshot.beans;
        }
        synchronized (FluentQueryFilterRuntimeResolver.class) {
            snapshot = cachedSnapshot;
            if (snapshot == null || snapshot.context != context) {
                snapshot = new BeansSnapshot<>(context, Collections.unmodifiableMap(
                        new LinkedHashMap<>(BeansUtils.getBeansOfType(FluentQueryFilterInjector.class))));
                cachedSnapshot = snapshot;
            }
            return snapshot.beans;
        }
    }
}
