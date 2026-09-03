package cn.geelato.core.mql.spi.support;

import cn.geelato.core.mql.spi.MqlSaveFieldValueFillContext;
import cn.geelato.core.mql.spi.MqlSaveFieldValueFiller;
import cn.geelato.core.util.BeansUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public final class MqlSaveFieldValueFillRuntimeResolver {
    private static final Logger log = LoggerFactory.getLogger(MqlSaveFieldValueFillRuntimeResolver.class);

    /**
     * 填充器 bean 每个ApplicationContext 实例只解析一次（无热部署注册场景）。
     * 缓存绑定容器实例身份：容器不变则永久复用，容器更换（如单测逐用例换 StaticApplicationContext）自动重解析。
     * 上下文未就绪（如 core 模块无容器的单测环境）每次直查且不缓存，避免把"未找到"空结果永久化。
     * 仅缓存 Spring 装配事实；isEnabled() 判断与 fill() 执行保持逐次进行。
     */
    private static volatile BeansSnapshot<MqlSaveFieldValueFiller> cachedSnapshot;

    private static final class BeansSnapshot<T> {
        final org.springframework.context.ApplicationContext context;
        final Map<String, T> beans;

        BeansSnapshot(org.springframework.context.ApplicationContext context, Map<String, T> beans) {
            this.context = context;
            this.beans = beans;
        }
    }

    private MqlSaveFieldValueFillRuntimeResolver() {
    }

    public static void fillIfAvailable(MqlSaveFieldValueFillContext context) {
        Entry<String, MqlSaveFieldValueFiller> fillerEntry = resolveUniqueFillerEntry(context);
        if (fillerEntry == null) {
            return;
        }
        MqlSaveFieldValueFiller filler = fillerEntry.getValue();
        boolean enabled = filler.isEnabled();
        log.debug("Resolved MQL save field value filler. entityName={}, commandType={}, beanName={}, beanClass={}, enabled={}",
                context.getEntityName(), context.getCommandType(), fillerEntry.getKey(), filler.getClass().getName(), enabled);
        if (!enabled) {
            log.debug("Skip MQL save field value fill because filler is disabled. entityName={}, commandType={}, beanName={}",
                    context.getEntityName(), context.getCommandType(), fillerEntry.getKey());
            return;
        }
        log.debug("Applying MQL save field value fill. entityName={}, commandType={}, beanName={}, beanClass={}",
                context.getEntityName(), context.getCommandType(), fillerEntry.getKey(), filler.getClass().getName());
        filler.fill(context);
        log.debug("Completed MQL save field value fill. entityName={}, commandType={}, beanName={}",
                context.getEntityName(), context.getCommandType(), fillerEntry.getKey());
    }

    static MqlSaveFieldValueFiller resolveUniqueFiller() {
        Entry<String, MqlSaveFieldValueFiller> fillerEntry = resolveUniqueFillerEntry(null);
        return fillerEntry == null ? null : fillerEntry.getValue();
    }

    static Entry<String, MqlSaveFieldValueFiller> resolveUniqueFillerEntry(MqlSaveFieldValueFillContext context) {
        Map<String, MqlSaveFieldValueFiller> beans = resolveBeansOnce();
        if (beans.isEmpty()) {
            log.debug("No MqlSaveFieldValueFiller bean found. Skip MQL save field value fill. entityName={}, commandType={}",
                    context == null ? null : context.getEntityName(), context == null ? null : context.getCommandType());
            return null;
        }
        if (beans.size() > 1) {
            List<String> beanNames = new ArrayList<>(beans.keySet());
            throw new IllegalStateException("Multiple MqlSaveFieldValueFiller beans found: " + beanNames + ". Expected 0 or 1.");
        }
        return beans.entrySet().iterator().next();
    }

    private static Map<String, MqlSaveFieldValueFiller> resolveBeansOnce() {
        org.springframework.context.ApplicationContext context = BeansUtils.getApplicationContext();
        if (context == null) {
            return BeansUtils.getBeansOfType(MqlSaveFieldValueFiller.class);
        }
        BeansSnapshot<MqlSaveFieldValueFiller> snapshot = cachedSnapshot;
        if (snapshot != null && snapshot.context == context) {
            return snapshot.beans;
        }
        synchronized (MqlSaveFieldValueFillRuntimeResolver.class) {
            snapshot = cachedSnapshot;
            if (snapshot == null || snapshot.context != context) {
                snapshot = new BeansSnapshot<>(context, Collections.unmodifiableMap(
                        new LinkedHashMap<>(BeansUtils.getBeansOfType(MqlSaveFieldValueFiller.class))));
                cachedSnapshot = snapshot;
            }
            return snapshot.beans;
        }
    }
}
