package cn.geelato.orm.spi.support;

import cn.geelato.core.util.BeansUtils;
import cn.geelato.orm.spi.FluentSaveFieldValueFillContext;
import cn.geelato.orm.spi.FluentSaveFieldValueFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Map.Entry;

public final class FluentSaveFieldValueFillRuntimeResolver {
    private static final Logger log = LoggerFactory.getLogger(FluentSaveFieldValueFillRuntimeResolver.class);

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
        Map<String, FluentSaveFieldValueFiller> beans = BeansUtils.getBeansOfType(FluentSaveFieldValueFiller.class);
        String entityName = context == null ? null : context.getEntityName();
        Object commandType = context == null ? null : context.getCommandType();
        if (beans.isEmpty()) {
            log.info("No FluentSaveFieldValueFiller bean found. Skip Fluent save field value fill. entityName={}, commandType={}",
                    entityName, commandType);
            return;
        }
        for (Entry<String, FluentSaveFieldValueFiller> entry : beans.entrySet()) {
            FluentSaveFieldValueFiller filler = entry.getValue();
            boolean enabled = filler.isEnabled();
            log.info("Resolved Fluent save field value filler. entityName={}, commandType={}, beanName={}, beanClass={}, enabled={}",
                    entityName, commandType, entry.getKey(), filler.getClass().getName(), enabled);
            if (!enabled) {
                log.info("Skip Fluent save field value fill because filler is disabled. entityName={}, commandType={}, beanName={}",
                        entityName, commandType, entry.getKey());
                continue;
            }
            log.info("Applying Fluent save field value fill. entityName={}, commandType={}, beanName={}, beanClass={}",
                    entityName, commandType, entry.getKey(), filler.getClass().getName());
            filler.fill(context);
            log.info("Completed Fluent save field value fill. entityName={}, commandType={}, beanName={}",
                    entityName, commandType, entry.getKey());
        }
    }
}
