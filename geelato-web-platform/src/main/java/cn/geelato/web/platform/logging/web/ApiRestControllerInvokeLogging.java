package cn.geelato.web.platform.logging.web;

import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.common.annotation.IgnoreSrvLog;
import cn.geelato.web.common.filter.CustomHttpServletRequest;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.platform.srvlog.model.SrvLogRecord;
import cn.geelato.web.platform.srvlog.service.SrvLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

@Slf4j
@RestControllerAdvice
public class ApiRestControllerInvokeLogging implements HandlerInterceptor, ResponseBodyAdvice<Object> {
    private static final String ATTR_ENABLED = "srvLog.enabled";
    private static final String ATTR_START = "srvLog.start";
    private static final String ATTR_HANDLER = "srvLog.handler";
    private static final String ATTR_CONTROLLER_CLASS = "srvLog.controllerClass";
    private static final String ATTR_METHOD_NAME = "srvLog.methodName";
    private static final String ATTR_PARAMS = "srvLog.params";
    private static final String ATTR_PATH_PARAMS = "srvLog.pathParams";
    private static final String ATTR_RESPONSE = "srvLog.response";
    private static final String ATTR_API_CODE = "srvLog.apiCode";
    private static final String ATTR_API_STATUS = "srvLog.apiStatus";
    private static final String ATTR_API_MSG = "srvLog.apiMsg";

    private final SrvLogService srvLogService;
    private final ObjectMapper objectMapper;

    public ApiRestControllerInvokeLogging(SrvLogService srvLogService, ObjectMapper objectMapper) {
        this.srvLogService = srvLogService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        Class<?> beanType = hm.getBeanType();
        if (!AnnotatedElementUtils.hasAnnotation(beanType, ApiRestController.class)) {
            return true;
        }
        if (isIgnored(beanType, hm)) {
            return true;
        }
        request.setAttribute(ATTR_ENABLED, Boolean.TRUE);
        request.setAttribute(ATTR_START, System.nanoTime());
        request.setAttribute(ATTR_HANDLER, hm.getMethod().toGenericString());
        request.setAttribute(ATTR_CONTROLLER_CLASS, beanType.getName());
        request.setAttribute(ATTR_METHOD_NAME, hm.getMethod().getName());

        Map<String, String[]> paramMap = request.getParameterMap();
        Map<String, List<String>> params = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> e : paramMap.entrySet()) {
            params.put(e.getKey(), e.getValue() == null ? Collections.emptyList() : Arrays.asList(e.getValue()));
        }
        request.setAttribute(ATTR_PARAMS, params);

        Object attr = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (attr instanceof Map<?, ?> m) {
            Map<String, String> pathVars = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                pathVars.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
            request.setAttribute(ATTR_PATH_PARAMS, pathVars);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, @Nullable Exception ex) {
        Object enabled = request.getAttribute(ATTR_ENABLED);
        if (!Boolean.TRUE.equals(enabled)) {
            return;
        }

        long durationMs = durationMs(request);
        int status = response.getStatus();
        String httpMethod = request.getMethod();
        Object p = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String pathPattern = p == null ? request.getRequestURI() : String.valueOf(p);
        String methodKey = httpMethod + " " + pathPattern;

        Map<String, Object> args = new LinkedHashMap<>();
        Object paramsAttr = request.getAttribute(ATTR_PARAMS);
        if (paramsAttr instanceof Map<?, ?>) {
            args.put("params", paramsAttr);
        }
        Object pathParamsAttr = request.getAttribute(ATTR_PATH_PARAMS);
        if (pathParamsAttr instanceof Map<?, ?>) {
            args.put("pathParams", pathParamsAttr);
        }
        if (request instanceof CustomHttpServletRequest cr) {
            String body = cr.getBody();
            if (body != null && !body.isBlank()) {
                args.put("body", body);
            }
        }

        String handlerSignature = attrString(request, ATTR_HANDLER);
        String controllerClass = attrString(request, ATTR_CONTROLLER_CLASS);
        String methodName = attrString(request, ATTR_METHOD_NAME);
        String resultJson = attrString(request, ATTR_RESPONSE);
        Integer apiCode = attrInt(request, ATTR_API_CODE);
        String apiStatus = attrString(request, ATTR_API_STATUS);
        String apiMsg = attrString(request, ATTR_API_MSG);

        boolean serviceFailure = ex != null || status != 200;
        boolean functionFailure = apiCode != null && apiCode != 20000;
        boolean success = !serviceFailure && !functionFailure;

        SrvLogRecord r = new SrvLogRecord();
        r.setTimestamp(System.currentTimeMillis());
        r.setMethodKey(methodKey);
        r.setHttpMethod(httpMethod);
        r.setPathPattern(pathPattern);
        r.setHandlerSignature(handlerSignature);
        r.setControllerClass(controllerClass);
        r.setMethodName(methodName);
        r.setArgsJson(srvLogService.toJson(args));
        r.setResultJson(resultJson);
        r.setSuccess(success);
        r.setStatus(status);
        r.setApiCode(apiCode);
        r.setApiStatus(apiStatus);
        r.setApiMsg(apiMsg);
        r.setDurationMs(durationMs);
        if (serviceFailure) {
            r.setErrorType("service");
            if (ex != null) {
                r.setExceptionClass(ex.getClass().getName());
                r.setExceptionMessage(ex.getMessage());
                r.setStackTrace(stackTrace(ex));
            } else {
                r.setExceptionClass("HttpStatusException");
                r.setExceptionMessage("HTTP status=" + status);
            }
        } else if (functionFailure) {
            r.setErrorType("function");
            r.setExceptionClass("FunctionException");
            if (apiMsg != null && !apiMsg.isBlank()) {
                r.setExceptionMessage(apiMsg);
            } else {
                r.setExceptionMessage("apiCode=" + apiCode);
            }
        }
        srvLogService.save(r);

        Map<String, Object> requestLog = new LinkedHashMap<>();
        requestLog.put("timestamp", r.getTimestamp());
        requestLog.put("method", httpMethod);
        requestLog.put("uri", request.getRequestURI());
        requestLog.put("url", url(request));
        requestLog.put("handler", handlerSignature);
        if (paramsAttr instanceof Map<?, ?>) {
            requestLog.put("params", paramsAttr);
        }
        if (pathParamsAttr instanceof Map<?, ?>) {
            requestLog.put("pathParams", pathParamsAttr);
        }
        if (resultJson != null && !resultJson.isEmpty()) {
            requestLog.put("response", truncate(resultJson, 10000));
        }
        requestLog.put("durationMs", durationMs);
        requestLog.put("status", status);

        try {
            log.info(objectMapper.writeValueAsString(requestLog));
        } catch (Exception e) {
            log.info(String.valueOf(requestLog));
        }
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> containingClass = returnType.getContainingClass();
        if (returnType.getMethod() == null) {
            return false;
        }
        return AnnotatedElementUtils.hasAnnotation(containingClass, ApiRestController.class)
                && !AnnotatedElementUtils.hasAnnotation(containingClass, IgnoreSrvLog.class)
                && !returnType.getMethod().isAnnotationPresent(IgnoreSrvLog.class);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        if (request instanceof ServletServerHttpRequest ssr) {
            HttpServletRequest req = ssr.getServletRequest();
            Object enabled = req.getAttribute(ATTR_ENABLED);
            if (!Boolean.TRUE.equals(enabled)) {
                return body;
            }
            String json;
            try {
                json = objectMapper.writeValueAsString(body);
            } catch (Exception e) {
                json = String.valueOf(body);
            }
            req.setAttribute(ATTR_RESPONSE, truncate(json, 10000));

            if (body instanceof ApiResult<?> ar) {
                req.setAttribute(ATTR_API_CODE, ar.getCode());
                req.setAttribute(ATTR_API_STATUS, ar.getStatus());
                req.setAttribute(ATTR_API_MSG, ar.getMsg());
            }
        }
        return body;
    }

    private boolean isIgnored(Class<?> beanType, HandlerMethod hm) {
        if (hm.getMethod().isAnnotationPresent(IgnoreSrvLog.class)) {
            return true;
        }
        return AnnotatedElementUtils.hasAnnotation(beanType, IgnoreSrvLog.class);
    }

    private long durationMs(HttpServletRequest request) {
        Object startAttr = request.getAttribute(ATTR_START);
        long start = startAttr instanceof Long ? (Long) startAttr : 0L;
        return start > 0 ? (System.nanoTime() - start) / 1_000_000 : 0L;
    }

    private String url(HttpServletRequest request) {
        String url = request.getRequestURL().toString();
        String qs = request.getQueryString();
        if (qs != null && !qs.isEmpty()) {
            return url + "?" + qs;
        }
        return url;
    }

    private String attrString(HttpServletRequest request, String key) {
        Object v = request.getAttribute(key);
        return v == null ? null : String.valueOf(v);
    }

    private Integer attrInt(HttpServletRequest request, String key) {
        Object v = request.getAttribute(key);
        if (v instanceof Integer i) {
            return i;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private String stackTrace(Throwable t) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
            pw.flush();
            return sw.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null || maxLen <= 0) {
            return s;
        }
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
