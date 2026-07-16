package cn.geelato.orm.spi.support;

import cn.geelato.core.util.BeansUtils;
import cn.geelato.orm.spi.FluentSaveFieldValueFillContext;
import cn.geelato.orm.spi.FluentSaveFieldValueFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public final class FluentSaveFieldValueFillRuntimeResolver {
    private static final Logger log = LoggerFactory.getLogger(FluentSaveFieldValueFillRuntimeResolver.class);

    private FluentSaveFieldValueFillRuntimeResolver() {
    }

    public static void fillIfAvailable(FluentSaveFieldValueFillContext context) {
        Entry<String, FluentSaveFieldValueFiller> fillerEntry = resolveUniqueFillerEntry(context);
        if (fillerEntry == null) {
            return;
        }
        FluentSaveFieldValueFiller filler = fillerEntry.getValue();
        boolean enabled = filler.isEnabled();
        log.info("Resolved Fluent save field value filler. entityName={}, commandType={}, beanName={}, beanClass={}, enabled={}",
                context.getEntityName(), context.getCommandType(), fillerEntry.getKey(), filler.getClass().getName(), enabled);
        if (!enabled) {
            log.info("Skip Fluent save field value fill because filler is disabled. entityName={}, commandType={}, beanName={}",
                    context.getEntityName(), context.getCommandType(), fillerEntry.getKey());
            return;
        }
        log.info("Applying Fluent save field value fill. entityName={}, commandType={}, beanName={}, beanClass={}",
                context.getEntityName(), context.getCommandType(), fillerEntry.getKey(), filler.getClass().getName());
        filler.fill(context);
        log.info("Completed Fluent save field value fill. entityName={}, commandType={}, beanName={}",
                context.getEntityName(), context.getCommandType(), fillerEntry.getKey());
    }

    static FluentSaveFieldValueFiller resolveUniqueFiller() {
        Entry<String, FluentSaveFieldValueFiller> fillerEntry = resolveUniqueFillerEntry(null);
        return fillerEntry == null ? null : fillerEntry.getValue();
    }

    static Entry<String, FluentSaveFieldValueFiller> resolveUniqueFillerEntry(FluentSaveFieldValueFillContext context) {
        Map<String, FluentSaveFieldValueFiller> beans = BeansUtils.getBeansOfType(FluentSaveFieldValueFiller.class);
        if (beans.isEmpty()) {
            log.info("No FluentSaveFieldValueFiller bean found. Skip Fluent save field value fill. entityName={}, commandType={}",
                    context == null ? null : context.getEntityName(), context == null ? null : context.getCommandType());
            return null;
        }
        if (beans.size() > 1) {
            List<String> beanNames = new ArrayList<>(beans.keySet());
            log.info("Detected multiple FluentSaveFieldValueFiller beans. entityName={}, commandType={}, beanNames={}",
                    context == null ? null : context.getEntityName(), context == null ? null : context.getCommandType(), beanNames);
            throw new IllegalStateException("Multiple FluentSaveFieldValueFiller beans found: " + beanNames + ". Expected 0 or 1.");
        }
        return beans.entrySet().iterator().next();
    }
}
