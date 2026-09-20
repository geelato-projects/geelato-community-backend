package cn.geelato.core.mql.spi.support;

import cn.geelato.core.experiment.ExperimentFeatures;
import cn.geelato.core.experiment.ExperimentGate;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.spi.MqlFuzzymatchRouter;
import cn.geelato.core.util.BeansUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * fuzzymatch 路由器解析：Spring 上下文中至多一个 {@link MqlFuzzymatchRouter} bean。
 *
 * <p>路由器异常不阻断查询——记录告警后走 SQL 层 REGEXP 等价改写兜底，
 * 保证检索引擎故障只降级性能、不影响可用性与结果集。
 */
public final class MqlFuzzymatchRouteResolver {
    private static final Logger log = LoggerFactory.getLogger(MqlFuzzymatchRouteResolver.class);

    /** 每个 ApplicationContext 实例只解析一次;容器未就绪时不缓存,逐次直查 */
    private static volatile BeansSnapshot<MqlFuzzymatchRouter> cachedSnapshot;

    private record BeansSnapshot<T>(ApplicationContext context, Map<String, T> beans) {
    }

    private MqlFuzzymatchRouteResolver() {
    }

    public static void routeIfAvailable(QueryCommand command) {
        // [experiment:search] 实验分支点 —— 毕业时删除本判定与 return 分支，仅保留路由路径
        if (!ExperimentGate.isEnabled(ExperimentFeatures.SEARCH)) {
            return;
        }
        Map<String, MqlFuzzymatchRouter> beans = resolveBeansOnce();
        if (beans.isEmpty()) {
            log.debug("未发现 MqlFuzzymatchRouter bean，fuzzymatch 查询走 SQL 兜底（检查搜索模块装配与 geelato.search.enabled）");
            return;
        }
        if (beans.size() > 1) {
            List<String> beanNames = new ArrayList<>(beans.keySet());
            throw new IllegalStateException("Multiple MqlFuzzymatchRouter beans found: " + beanNames + ". Expected 0 or 1.");
        }
        MqlFuzzymatchRouter router = beans.values().iterator().next();
        try {
            boolean routed = router.route(command);
            log.debug("MQL fuzzymatch route. entityName={}, routed={}", command.getEntityName(), routed);
        } catch (Exception ex) {
            log.warn("MQL fuzzymatch route failed, fallback to SQL REGEXP rewrite. entityName={}",
                    command.getEntityName(), ex);
        }
    }

    private static Map<String, MqlFuzzymatchRouter> resolveBeansOnce() {
        org.springframework.context.ApplicationContext context = BeansUtils.getApplicationContext();
        if (context == null) {
            return BeansUtils.getBeansOfType(MqlFuzzymatchRouter.class);
        }
        BeansSnapshot<MqlFuzzymatchRouter> snapshot = cachedSnapshot;
        if (snapshot != null && snapshot.context == context) {
            return snapshot.beans;
        }
        synchronized (MqlFuzzymatchRouteResolver.class) {
            snapshot = cachedSnapshot;
            if (snapshot == null || snapshot.context != context) {
                snapshot = new BeansSnapshot<>(context, java.util.Collections.unmodifiableMap(
                        new java.util.LinkedHashMap<>(BeansUtils.getBeansOfType(MqlFuzzymatchRouter.class))));
                cachedSnapshot = snapshot;
            }
            return snapshot.beans;
        }
    }
}
