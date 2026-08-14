package cn.geelato.web.platform.srvlog.registry;

import cn.geelato.web.common.annotation.ApiRestController;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.*;

@Component
public class ApiRestControllerSrvLogRegistry {
    private final RequestMappingHandlerMapping handlerMapping;

    @Getter
    private final List<ApiRestControllerSrvLogDefinition> definitions = new ArrayList<>();

    private final Map<String, String> methodKeyToHandlerSignature = new HashMap<>();

    public ApiRestControllerSrvLogRegistry(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    /**
     * 应用就绪后再扫描注册 ApiRestController 端点，避免在启动主线程遍历 handler 映射。
     * 扫描完成前 {@link #resolveHandlerSignature(String)} 返回 null，仅影响该时段的服务日志归属。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : handlerMethods.entrySet()) {
            HandlerMethod hm = e.getValue();
            Class<?> beanType = hm.getBeanType();
            if (!AnnotatedElementUtils.hasAnnotation(beanType, ApiRestController.class)) {
                continue;
            }
            RequestMappingInfo info = e.getKey();
            Set<RequestMethod> requestMethods = info.getMethodsCondition().getMethods();
            Set<String> patterns = resolvePatterns(info);
            List<String> candidates = new ArrayList<>();
            if (requestMethods.isEmpty()) {
                for (String p : patterns) {
                    candidates.add("* " + p);
                }
            } else {
                for (RequestMethod rm : requestMethods) {
                    for (String p : patterns) {
                        candidates.add(rm.name() + " " + p);
                    }
                }
            }
            candidates.sort(Comparator.naturalOrder());
            ApiRestControllerSrvLogDefinition def = new ApiRestControllerSrvLogDefinition();
            def.setControllerClass(beanType.getName());
            def.setMethodName(hm.getMethod().getName());
            def.setHandlerSignature(hm.getMethod().toGenericString());
            def.setMethodKeyCandidates(Collections.unmodifiableList(candidates));
            definitions.add(def);
            for (String mk : candidates) {
                methodKeyToHandlerSignature.putIfAbsent(mk, def.getHandlerSignature());
            }
        }
    }

    public String resolveHandlerSignature(String methodKey) {
        return methodKeyToHandlerSignature.get(methodKey);
    }

    private Set<String> resolvePatterns(RequestMappingInfo info) {
        if (info.getPathPatternsCondition() != null) {
            return info.getPathPatternsCondition().getPatternValues();
        }
        if (info.getPatternsCondition() != null) {
            return info.getPatternsCondition().getPatterns();
        }
        return Collections.emptySet();
    }
}

