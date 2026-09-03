package cn.geelato.core.mql.spi.support;

import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.spi.MqlQueryFilterInjector;
import cn.geelato.core.util.BeansUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public final class MqlQueryFilterRuntimeResolver {
    private static final Logger log = LoggerFactory.getLogger(MqlQueryFilterRuntimeResolver.class);

    /** 每个 ApplicationContext 实例只解析一次;容器未就绪时不缓存,逐次直查 */
    private static volatile BeansSnapshot<MqlQueryFilterInjector> cachedSnapshot;

    private static final class BeansSnapshot<T> {
        final org.springframework.context.ApplicationContext context;
        final Map<String, T> beans;

        BeansSnapshot(org.springframework.context.ApplicationContext context, Map<String, T> beans) {
            this.context = context;
            this.beans = beans;
        }
    }

    private MqlQueryFilterRuntimeResolver() {
    }

    public static void injectIfAvailable(QueryCommand command) {
        Entry<String, MqlQueryFilterInjector> injectorEntry = resolveUniqueInjectorEntry(command);
        if (injectorEntry == null) {
            return;
        }
        MqlQueryFilterInjector injector = injectorEntry.getValue();
        boolean enabled = injector.isEnabled();
        log.debug("Resolved MQL query filter injector. entityName={}, beanName={}, beanClass={}, enabled={}",
                command.getEntityName(), injectorEntry.getKey(), injector.getClass().getName(), enabled);
        if (!enabled) {
            log.debug("Skip MQL query filter injection because injector is disabled. entityName={}, beanName={}",
                    command.getEntityName(), injectorEntry.getKey());
            return;
        }
        log.debug("Applying MQL query filter injection. entityName={}, beanName={}, beanClass={}",
                command.getEntityName(), injectorEntry.getKey(), injector.getClass().getName());
        injector.inject(command);
        log.debug("Completed MQL query filter injection. entityName={}, beanName={}",
                command.getEntityName(), injectorEntry.getKey());
    }

    static MqlQueryFilterInjector resolveUniqueInjector() {
        Entry<String, MqlQueryFilterInjector> injectorEntry = resolveUniqueInjectorEntry(null);
        return injectorEntry == null ? null : injectorEntry.getValue();
    }

    static Entry<String, MqlQueryFilterInjector> resolveUniqueInjectorEntry(QueryCommand command) {
        Map<String, MqlQueryFilterInjector> beans = resolveBeansOnce();
        if (beans.isEmpty()) {
            log.debug("No MqlQueryFilterInjector bean found. Skip MQL query filter injection. entityName={}",
                    command == null ? null : command.getEntityName());
            return null;
        }
        if (beans.size() > 1) {
            List<String> beanNames = new ArrayList<>(beans.keySet());
            throw new IllegalStateException("Multiple MqlQueryFilterInjector beans found: " + beanNames + ". Expected 0 or 1.");
        }
        return beans.entrySet().iterator().next();
    }

    private static Map<String, MqlQueryFilterInjector> resolveBeansOnce() {
        org.springframework.context.ApplicationContext context = BeansUtils.getApplicationContext();
        if (context == null) {
            return BeansUtils.getBeansOfType(MqlQueryFilterInjector.class);
        }
        BeansSnapshot<MqlQueryFilterInjector> snapshot = cachedSnapshot;
        if (snapshot != null && snapshot.context == context) {
            return snapshot.beans;
        }
        synchronized (MqlQueryFilterRuntimeResolver.class) {
            snapshot = cachedSnapshot;
            if (snapshot == null || snapshot.context != context) {
                snapshot = new BeansSnapshot<>(context, java.util.Collections.unmodifiableMap(
                        new java.util.LinkedHashMap<>(BeansUtils.getBeansOfType(MqlQueryFilterInjector.class))));
                cachedSnapshot = snapshot;
            }
            return snapshot.beans;
        }
    }
}
