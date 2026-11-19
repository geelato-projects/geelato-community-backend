package cn.geelato.web.platform.run.monitor.methodstats;

import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import cn.geelato.web.common.annotation.DesignTimeApiRestController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Controller 方法调用计数（纯内存，重启即清零）。
 * <p>
 * 启动完成后枚举全部 API handler 预置 0 值（未调用的也展示）；仅统计类上带 Api 三注解的
 * {@code /api} 接口方法，口径与 ApiPrefixAutoConfiguration 的前缀判断一致。重载方法合并计数。
 */
@Component
public class ControllerMethodCallStats {

    /** 条目上限，防止异常流量（随机路径）撑爆内存 */
    private static final int MAX_KEYS = 5000;

    private final ConcurrentHashMap<String, StatsEntry> entries = new ConcurrentHashMap<>();
    private final LongAdder overflow = new LongAdder();

    // @Lazy 断循环依赖：EnableWebMvcConfiguration 初始化收集 WebMvcConfigurer → configuration → 本类 → RequestMappingHandlerMapping；
    // 注入代理推迟到 ApplicationReadyEvent 使用时才解析
    @Autowired
    @Lazy
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @EventListener(ApplicationReadyEvent.class)
    public void registerAllApiHandlers() {
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : requestMappingHandlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod hm = e.getValue();
            if (isApiController(hm.getBeanType())) {
                registerZero(hm, e.getKey());
            }
        }
    }

    public void record(HandlerMethod handler, HttpServletRequest request) {
        if (!isApiController(handler.getBeanType())) {
            return;
        }
        String key = statsKey(handler);
        StatsEntry entry = entries.get(key);
        if (entry == null) {
            if (entries.size() >= MAX_KEYS) {
                overflow.increment();
                return;
            }
            String path = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            entry = entries.computeIfAbsent(key,
                    k -> new StatsEntry(handler.getBeanType().getSimpleName(),
                            handler.getMethod().getName(), "-", path == null ? "-" : path));
        }
        entry.count.increment();
    }

    public Map<String, Object> snapshot() {
        List<StatsEntry> list = new ArrayList<>(entries.values());
        list.sort(Comparator.comparingLong((StatsEntry e) -> e.count.sum()).reversed());
        List<Map<String, Object>> items = new ArrayList<>(list.size());
        long totalCalls = 0;
        long calledEndpoints = 0;
        for (StatsEntry e : list) {
            long c = e.count.sum();
            totalCalls += c;
            if (c > 0) {
                calledEndpoints++;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("controller", e.controller);
            m.put("method", e.method);
            m.put("httpMethods", e.httpMethods);
            m.put("path", e.path);
            m.put("count", c);
            items.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("totalEndpoints", entries.size());
        result.put("calledEndpoints", calledEndpoints);
        result.put("totalCalls", totalCalls);
        result.put("overflow", overflow.sum());
        return result;
    }

    /** 清零但保留条目，0 值展示不丢失 */
    public void resetAll() {
        for (StatsEntry e : entries.values()) {
            e.count.reset();
        }
        overflow.reset();
    }

    private void registerZero(HandlerMethod hm, RequestMappingInfo info) {
        String key = statsKey(hm);
        entries.putIfAbsent(key, new StatsEntry(
                hm.getBeanType().getSimpleName(), hm.getMethod().getName(),
                joinHttpMethods(info), joinPaths(info)));
    }

    private String statsKey(HandlerMethod hm) {
        return hm.getBeanType().getName() + "#" + hm.getMethod().getName();
    }

    static boolean isApiController(Class<?> beanType) {
        return AnnotatedElementUtils.findMergedAnnotation(beanType, ApiRestController.class) != null
                || AnnotatedElementUtils.findMergedAnnotation(beanType, ApiRuntimeRestController.class) != null
                || AnnotatedElementUtils.findMergedAnnotation(beanType, DesignTimeApiRestController.class) != null;
    }

    private String joinHttpMethods(RequestMappingInfo info) {
        Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
        if (methods.isEmpty()) {
            return "ANY";
        }
        List<String> names = new ArrayList<>(methods.size());
        for (RequestMethod m : methods) {
            names.add(m.name());
        }
        names.sort(String::compareTo);
        return String.join(",", names);
    }

    private String joinPaths(RequestMappingInfo info) {
        Set<String> patterns = info.getPatternValues();
        if (patterns.isEmpty()) {
            return "-";
        }
        List<String> sorted = new ArrayList<>(patterns);
        sorted.sort(String::compareTo);
        return String.join(",", sorted);
    }

    static class StatsEntry {
        final String controller;
        final String method;
        final String httpMethods;
        final String path;
        final LongAdder count = new LongAdder();

        StatsEntry(String controller, String method, String httpMethods, String path) {
            this.controller = controller;
            this.method = method;
            this.httpMethods = httpMethods;
            this.path = path;
        }
    }
}
