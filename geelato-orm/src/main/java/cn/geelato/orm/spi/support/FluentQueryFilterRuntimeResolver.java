package cn.geelato.orm.spi.support;

import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.util.BeansUtils;
import cn.geelato.orm.query.MetaQuery;
import cn.geelato.orm.spi.FluentQueryFilterInjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public final class FluentQueryFilterRuntimeResolver {
    private static final Logger log = LoggerFactory.getLogger(FluentQueryFilterRuntimeResolver.class);

    private FluentQueryFilterRuntimeResolver() {
    }

    public static void injectIfAvailable(QueryCommand command, MetaQuery query) {
        Entry<String, FluentQueryFilterInjector> injectorEntry = resolveUniqueInjectorEntry(query);
        if (injectorEntry == null) {
            return;
        }
        String entityName = entityNameOf(query);
        String beanName = injectorEntry.getKey();
        FluentQueryFilterInjector injector = injectorEntry.getValue();
        String beanClass = injector.getClass().getName();
        if (!injector.isEnabled()) {
            log.info("Skip Fluent query filter injection because injector is disabled. entityName={}, beanName={}, beanClass={}",
                    entityName, beanName, beanClass);
            return;
        }
        log.info("Applying Fluent query filter injection. entityName={}, beanName={}, beanClass={}", entityName, beanName, beanClass);
        injector.inject(command, query);
        log.info("Completed Fluent query filter injection. entityName={}, beanName={}", entityName, beanName);
    }

    static FluentQueryFilterInjector resolveUniqueInjector() {
        Entry<String, FluentQueryFilterInjector> injectorEntry = resolveUniqueInjectorEntry(null);
        return injectorEntry == null ? null : injectorEntry.getValue();
    }

    static Entry<String, FluentQueryFilterInjector> resolveUniqueInjectorEntry(MetaQuery query) {
        String entityName = entityNameOf(query);
        Map<String, FluentQueryFilterInjector> beans = BeansUtils.getBeansOfType(FluentQueryFilterInjector.class);
        if (beans.isEmpty()) {
            log.info("No FluentQueryFilterInjector bean found. Skip Fluent query filter injection. entityName={}", entityName);
            return null;
        }
        // 只在“启用”的注入器之间判定唯一性：被禁用（isEnabled()=false）的 Bean 视为不存在，不参与冲突。
        // 这样允许同时注册多个注入器（如平台默认 + 应用自定义），只要运行期至多有一个处于启用态。
        List<Entry<String, FluentQueryFilterInjector>> enabledEntries = beans.entrySet().stream()
                .filter(entry -> entry.getValue().isEnabled())
                .collect(Collectors.toList());
        if (enabledEntries.isEmpty()) {
            log.info("No enabled FluentQueryFilterInjector among {} bean(s). Skip Fluent query filter injection. entityName={}, beanNames={}",
                    beans.size(), entityName, beans.keySet());
            return null;
        }
        if (enabledEntries.size() > 1) {
            List<String> enabledNames = enabledEntries.stream().map(Entry::getKey).collect(Collectors.toList());
            log.info("Detected multiple enabled FluentQueryFilterInjector beans. entityName={}, enabledBeanNames={}", entityName, enabledNames);
            throw new IllegalStateException("Multiple enabled FluentQueryFilterInjector beans found: " + enabledNames + ". Expected at most 1 enabled.");
        }
        return enabledEntries.get(0);
    }

    private static String entityNameOf(MetaQuery query) {
        return query == null ? null : query.resolveEntityName();
    }
}
