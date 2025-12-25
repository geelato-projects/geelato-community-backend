package cn.geelato.web.platform.boot.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.*;

@Component
public class ControllerInvokeLoggingInterceptor implements HandlerInterceptor {
    private final ControllerCallRecorder recorder;
    public ControllerInvokeLoggingInterceptor(@Qualifier("logbackControllerCallRecorder") ControllerCallRecorder recorder, ObjectMapper objectMapper) {
        this.recorder = recorder;
    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute("ri.start", System.nanoTime());
        if (handler instanceof HandlerMethod) {
            HandlerMethod hm = (HandlerMethod) handler;
            request.setAttribute("ri.handler", hm.getMethod().toGenericString());
        }
        Map<String, String[]> paramMap = request.getParameterMap();
        Map<String, List<String>> params = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> e : paramMap.entrySet()) {
            params.put(e.getKey(), e.getValue() == null ? Collections.emptyList() : Arrays.asList(e.getValue()));
        }
        request.setAttribute("ri.params", params);
        Object attr = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (attr instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) attr;
            Map<String, String> pathVars = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                pathVars.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
            request.setAttribute("ri.pathParams", pathVars);
        }
        return true;
    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) {
        Object startAttr = request.getAttribute("ri.start");
        long start = startAttr instanceof Long ? (Long) startAttr : 0L;
        long durationMs = start > 0 ? (System.nanoTime() - start) / 1_000_000 : 0L;
        String responseJson = String.valueOf(request.getAttribute("ri.response"));
        ControllerInvocationLog log = new ControllerInvocationLog();
        log.setTimestamp(System.currentTimeMillis());
        log.setMethod(request.getMethod());
        log.setUri(request.getRequestURI());
        String url = request.getRequestURL().toString();
        String qs = request.getQueryString();
        if (qs != null && !qs.isEmpty()) {
            url = url + "?" + qs;
        }
        log.setUrl(url);
        Object handlerAttr = request.getAttribute("ri.handler");
        log.setHandler(handlerAttr == null ? null : String.valueOf(handlerAttr));
        Object paramsAttr = request.getAttribute("ri.params");
        if (paramsAttr instanceof Map) {
            log.setParams((Map<String, List<String>>) paramsAttr);
        }
        Object pathParamsAttr = request.getAttribute("ri.pathParams");
        if (pathParamsAttr instanceof Map) {
            log.setPathParams((Map<String, String>) pathParamsAttr);
        }
        if (responseJson != null && !responseJson.isEmpty()) {
            if (responseJson.length() > 10000) {
                responseJson = responseJson.substring(0, 10000);
            }
            log.setResponse(responseJson);
        }
        log.setDurationMs(durationMs);
        log.setStatus(response.getStatus());
        recorder.record(log);
    }
}
