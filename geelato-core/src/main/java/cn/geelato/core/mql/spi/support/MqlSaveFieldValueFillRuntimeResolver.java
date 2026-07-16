package cn.geelato.core.mql.spi.support;

import cn.geelato.core.mql.spi.MqlSaveFieldValueFillContext;
import cn.geelato.core.mql.spi.MqlSaveFieldValueFiller;
import cn.geelato.core.util.BeansUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public final class MqlSaveFieldValueFillRuntimeResolver {
    private static final Logger log = LoggerFactory.getLogger(MqlSaveFieldValueFillRuntimeResolver.class);

    private MqlSaveFieldValueFillRuntimeResolver() {
    }

    public static void fillIfAvailable(MqlSaveFieldValueFillContext context) {
        Entry<String, MqlSaveFieldValueFiller> fillerEntry = resolveUniqueFillerEntry(context);
        if (fillerEntry == null) {
            return;
        }
        MqlSaveFieldValueFiller filler = fillerEntry.getValue();
        boolean enabled = filler.isEnabled();
        log.info("Resolved MQL save field value filler. entityName={}, commandType={}, beanName={}, beanClass={}, enabled={}",
                context.getEntityName(), context.getCommandType(), fillerEntry.getKey(), filler.getClass().getName(), enabled);
        if (!enabled) {
            log.info("Skip MQL save field value fill because filler is disabled. entityName={}, commandType={}, beanName={}",
                    context.getEntityName(), context.getCommandType(), fillerEntry.getKey());
            return;
        }
        log.info("Applying MQL save field value fill. entityName={}, commandType={}, beanName={}, beanClass={}",
                context.getEntityName(), context.getCommandType(), fillerEntry.getKey(), filler.getClass().getName());
        filler.fill(context);
        log.info("Completed MQL save field value fill. entityName={}, commandType={}, beanName={}",
                context.getEntityName(), context.getCommandType(), fillerEntry.getKey());
    }

    static MqlSaveFieldValueFiller resolveUniqueFiller() {
        Entry<String, MqlSaveFieldValueFiller> fillerEntry = resolveUniqueFillerEntry(null);
        return fillerEntry == null ? null : fillerEntry.getValue();
    }

    static Entry<String, MqlSaveFieldValueFiller> resolveUniqueFillerEntry(MqlSaveFieldValueFillContext context) {
        Map<String, MqlSaveFieldValueFiller> beans = BeansUtils.getBeansOfType(MqlSaveFieldValueFiller.class);
        if (beans.isEmpty()) {
            log.info("No MqlSaveFieldValueFiller bean found. Skip MQL save field value fill. entityName={}, commandType={}",
                    context == null ? null : context.getEntityName(), context == null ? null : context.getCommandType());
            return null;
        }
        if (beans.size() > 1) {
            List<String> beanNames = new ArrayList<>(beans.keySet());
            log.info("Detected multiple MqlSaveFieldValueFiller beans. entityName={}, commandType={}, beanNames={}",
                    context == null ? null : context.getEntityName(), context == null ? null : context.getCommandType(), beanNames);
            throw new IllegalStateException("Multiple MqlSaveFieldValueFiller beans found: " + beanNames + ". Expected 0 or 1.");
        }
        return beans.entrySet().iterator().next();
    }
}
