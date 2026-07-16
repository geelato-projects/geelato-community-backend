package cn.geelato.orm.spi.support;

import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.util.BeansUtils;
import cn.geelato.orm.query.MetaQuery;
import cn.geelato.orm.spi.FluentQueryFilterInjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public final class FluentQueryFilterRuntimeResolver {
    private static final Logger log = LoggerFactory.getLogger(FluentQueryFilterRuntimeResolver.class);

    private FluentQueryFilterRuntimeResolver() {
    }

    public static void injectIfAvailable(QueryCommand command, MetaQuery query) {
        Entry<String, FluentQueryFilterInjector> injectorEntry = resolveUniqueInjectorEntry(query);
        if (injectorEntry == null) {
            return;
        }
        FluentQueryFilterInjector injector = injectorEntry.getValue();
        boolean enabled = injector.isEnabled();
        log.info("Resolved Fluent query filter injector. entityName={}, beanName={}, beanClass={}, enabled={}",
                query == null ? null : query.resolveEntityName(), injectorEntry.getKey(), injector.getClass().getName(), enabled);
        if (!enabled) {
            log.info("Skip Fluent query filter injection because injector is disabled. entityName={}, beanName={}",
                    query == null ? null : query.resolveEntityName(), injectorEntry.getKey());
            return;
        }
        log.info("Applying Fluent query filter injection. entityName={}, beanName={}, beanClass={}",
                query == null ? null : query.resolveEntityName(), injectorEntry.getKey(), injector.getClass().getName());
        injector.inject(command, query);
        log.info("Completed Fluent query filter injection. entityName={}, beanName={}",
                query == null ? null : query.resolveEntityName(), injectorEntry.getKey());
    }

    static FluentQueryFilterInjector resolveUniqueInjector() {
        Entry<String, FluentQueryFilterInjector> injectorEntry = resolveUniqueInjectorEntry(null);
        return injectorEntry == null ? null : injectorEntry.getValue();
    }

    static Entry<String, FluentQueryFilterInjector> resolveUniqueInjectorEntry(MetaQuery query) {
        Map<String, FluentQueryFilterInjector> beans = BeansUtils.getBeansOfType(FluentQueryFilterInjector.class);
        if (beans.isEmpty()) {
            log.info("No FluentQueryFilterInjector bean found. Skip Fluent query filter injection. entityName={}",
                    query == null ? null : query.resolveEntityName());
            return null;
        }
        if (beans.size() > 1) {
            List<String> beanNames = new ArrayList<>(beans.keySet());
            log.info("Detected multiple FluentQueryFilterInjector beans. entityName={}, beanNames={}",
                    query == null ? null : query.resolveEntityName(), beanNames);
            throw new IllegalStateException("Multiple FluentQueryFilterInjector beans found: " + beanNames + ". Expected 0 or 1.");
        }
        return beans.entrySet().iterator().next();
    }
}
