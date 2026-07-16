package cn.geelato.core.meta.spi.support;

import cn.geelato.core.meta.spi.EntitySaveFieldValueFillContext;
import cn.geelato.core.meta.spi.EntitySaveFieldValueFiller;
import cn.geelato.core.util.BeansUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public final class EntitySaveFieldValueFillRuntimeResolver {
    private static final Logger log = LoggerFactory.getLogger(EntitySaveFieldValueFillRuntimeResolver.class);

    private EntitySaveFieldValueFillRuntimeResolver() {
    }

    public static void fillIfAvailable(EntitySaveFieldValueFillContext context) {
        Entry<String, EntitySaveFieldValueFiller> fillerEntry = resolveUniqueFillerEntry(context);
        if (fillerEntry == null) {
            return;
        }
        EntitySaveFieldValueFiller filler = fillerEntry.getValue();
        boolean enabled = filler.isEnabled();
        log.info("Resolved entity save field value filler. entityName={}, commandType={}, beanName={}, beanClass={}, enabled={}",
                context.getEntityName(), context.getCommandType(), fillerEntry.getKey(), filler.getClass().getName(), enabled);
        if (!enabled) {
            log.info("Skip entity save field value fill because filler is disabled. entityName={}, commandType={}, beanName={}",
                    context.getEntityName(), context.getCommandType(), fillerEntry.getKey());
            return;
        }
        log.info("Applying entity save field value fill. entityName={}, commandType={}, beanName={}, beanClass={}",
                context.getEntityName(), context.getCommandType(), fillerEntry.getKey(), filler.getClass().getName());
        filler.fill(context);
        log.info("Completed entity save field value fill. entityName={}, commandType={}, beanName={}",
                context.getEntityName(), context.getCommandType(), fillerEntry.getKey());
    }

    static EntitySaveFieldValueFiller resolveUniqueFiller() {
        Entry<String, EntitySaveFieldValueFiller> fillerEntry = resolveUniqueFillerEntry(null);
        return fillerEntry == null ? null : fillerEntry.getValue();
    }

    static Entry<String, EntitySaveFieldValueFiller> resolveUniqueFillerEntry(EntitySaveFieldValueFillContext context) {
        Map<String, EntitySaveFieldValueFiller> beans = BeansUtils.getBeansOfType(EntitySaveFieldValueFiller.class);
        if (beans.isEmpty()) {
            log.info("No EntitySaveFieldValueFiller bean found. Skip entity save field value fill. entityName={}, commandType={}",
                    context == null ? null : context.getEntityName(), context == null ? null : context.getCommandType());
            return null;
        }
        if (beans.size() > 1) {
            List<String> beanNames = new ArrayList<>(beans.keySet());
            log.info("Detected multiple EntitySaveFieldValueFiller beans. entityName={}, commandType={}, beanNames={}",
                    context == null ? null : context.getEntityName(), context == null ? null : context.getCommandType(), beanNames);
            throw new IllegalStateException("Multiple EntitySaveFieldValueFiller beans found: " + beanNames + ". Expected 0 or 1.");
        }
        return beans.entrySet().iterator().next();
    }
}
