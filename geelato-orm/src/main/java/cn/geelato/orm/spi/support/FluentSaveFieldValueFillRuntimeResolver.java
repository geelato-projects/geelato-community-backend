package cn.geelato.orm.spi.support;

import cn.geelato.core.util.BeansUtils;
import cn.geelato.orm.spi.FluentSaveFieldValueFillContext;
import cn.geelato.orm.spi.FluentSaveFieldValueFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public final class FluentSaveFieldValueFillRuntimeResolver {
    private static final Logger log = LoggerFactory.getLogger(FluentSaveFieldValueFillRuntimeResolver.class);

    /**
     * 填充器 bean 每个ApplicationContext 实例只解析一次（无热部署注册场景）。
     * 缓存绑定容器实例身份：容器不变则永久复用，容器更换（如单测逐用例换 mock 容器）自动重解析。
     * 上下文未就绪（如无容器的单测环境）每次直查且不缓存，避免把"未找到"空结果永久化。
     * 仅缓存 Spring 装配事实；isEnabled() 判断与 fill() 执行保持逐次进行。
     */
    private static volatile BeansSnapshot<FluentSaveFieldValueFiller> cachedSnapshot;

    private static final class BeansSnapshot<T> {
        final org.springframework.context.ApplicationContext context;
        final Map<String, T> beans;

        BeansSnapshot(org.springframework.context.ApplicationContext context, Map<String, T> beans) {
            this.context = context;
            this.beans = beans;
        }
    }

    private FluentSaveFieldValueFillRuntimeResolver() {
    }

    /**
     * 依次执行所有“启用”的保存字段填充器。
     * <p>
     * 与过滤注入（{@link FluentQueryFilterRuntimeResolver}，全局至多允许一个启用）不同，保存填充点<b>允许注册多个填充器</b>：
     * 这里会遍历所有 {@link FluentSaveFieldValueFiller} Bean，逐个执行处于启用态（{@code isEnabled()=true}）的填充器，
     * 禁用的跳过。多个填充器之间互不排斥，可叠加填充不同字段。
     * </p>
     */
    public static void fillIfAvailable(FluentSaveFieldValueFillContext context) {
        Map<String, FluentSaveFieldValueFiller> beans = resolveBeansOnce();
        String entityName = context == null ? null : context.getEntityName();
        Object commandType = context == null ? null : context.getCommandType();
        if (beans.isEmpty()) {
            log.debug("No FluentSaveFieldValueFiller bean found. Skip Fluent save field value fill. entityName={}, commandType={}",
                    entityName, commandType);
            return;
        }
        for (Entry<String, FluentSaveFieldValueFiller> entry : beans.entrySet()) {
            FluentSaveFieldValueFiller filler = entry.getValue();
            boolean enabled = filler.isEnabled();
            log.debug("Resolved Fluent save field value filler. entityName={}, commandType={}, beanName={}, beanClass={}, enabled={}",
                    entityName, commandType, entry.getKey(), filler.getClass().getName(), enabled);
            if (!enabled) {
                log.debug("Skip Fluent save field value fill because filler is disabled. entityName={}, commandType={}, beanName={}",
                        entityName, commandType, entry.getKey());
                continue;
            }
            log.debug("Applying Fluent save field value fill. entityName={}, commandType={}, beanName={}, beanClass={}",
                    entityName, commandType, entry.getKey(), filler.getClass().getName());
            filler.fill(context);
            log.debug("Completed Fluent save field value fill. entityName={}, commandType={}, beanName={}",
                    entityName, commandType, entry.getKey());
        }
    }

    private static Map<String, FluentSaveFieldValueFiller> resolveBeansOnce() {
        org.springframework.context.ApplicationContext context = BeansUtils.getApplicationContext();
        if (context == null) {
            return BeansUtils.getBeansOfType(FluentSaveFieldValueFiller.class);
        }
        BeansSnapshot<FluentSaveFieldValueFiller> snapshot = cachedSnapshot;
        if (snapshot != null && snapshot.context == context) {
            return snapshot.beans;
        }
        synchronized (FluentSaveFieldValueFillRuntimeResolver.class) {
            snapshot = cachedSnapshot;
            if (snapshot == null || snapshot.context != context) {
                snapshot = new BeansSnapshot<>(context, Collections.unmodifiableMap(
                        new LinkedHashMap<>(BeansUtils.getBeansOfType(FluentSaveFieldValueFiller.class))));
                cachedSnapshot = snapshot;
            }
            return snapshot.beans;
        }
    }
}
