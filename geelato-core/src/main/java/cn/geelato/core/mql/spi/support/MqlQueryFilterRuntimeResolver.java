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

    private MqlQueryFilterRuntimeResolver() {
    }

    public static void injectIfAvailable(QueryCommand command) {
        Entry<String, MqlQueryFilterInjector> injectorEntry = resolveUniqueInjectorEntry(command);
        if (injectorEntry == null) {
            return;
        }
        MqlQueryFilterInjector injector = injectorEntry.getValue();
        boolean enabled = injector.isEnabled();
        log.info("Resolved MQL query filter injector. entityName={}, beanName={}, beanClass={}, enabled={}",
                command.getEntityName(), injectorEntry.getKey(), injector.getClass().getName(), enabled);
        if (!enabled) {
            log.info("Skip MQL query filter injection because injector is disabled. entityName={}, beanName={}",
                    command.getEntityName(), injectorEntry.getKey());
            return;
        }
        log.info("Applying MQL query filter injection. entityName={}, beanName={}, beanClass={}",
                command.getEntityName(), injectorEntry.getKey(), injector.getClass().getName());
        injector.inject(command);
        log.info("Completed MQL query filter injection. entityName={}, beanName={}",
                command.getEntityName(), injectorEntry.getKey());
    }

    static MqlQueryFilterInjector resolveUniqueInjector() {
        Entry<String, MqlQueryFilterInjector> injectorEntry = resolveUniqueInjectorEntry(null);
        return injectorEntry == null ? null : injectorEntry.getValue();
    }

    static Entry<String, MqlQueryFilterInjector> resolveUniqueInjectorEntry(QueryCommand command) {
        Map<String, MqlQueryFilterInjector> beans = BeansUtils.getBeansOfType(MqlQueryFilterInjector.class);
        if (beans.isEmpty()) {
            log.info("No MqlQueryFilterInjector bean found. Skip MQL query filter injection. entityName={}",
                    command == null ? null : command.getEntityName());
            return null;
        }
        if (beans.size() > 1) {
            List<String> beanNames = new ArrayList<>(beans.keySet());
            log.info("Detected multiple MqlQueryFilterInjector beans. entityName={}, beanNames={}",
                    command == null ? null : command.getEntityName(), beanNames);
            throw new IllegalStateException("Multiple MqlQueryFilterInjector beans found: " + beanNames + ". Expected 0 or 1.");
        }
        return beans.entrySet().iterator().next();
    }
}
