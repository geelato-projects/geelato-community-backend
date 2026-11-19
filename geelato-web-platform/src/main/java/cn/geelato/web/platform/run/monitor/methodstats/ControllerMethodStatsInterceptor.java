package cn.geelato.web.platform.run.monitor.methodstats;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Controller 方法调用统计拦截器。注册顺序在鉴权拦截器之后，被 401 拒绝的请求不计数。
 */
public class ControllerMethodStatsInterceptor implements HandlerInterceptor {

    private final ControllerMethodCallStats stats;

    public ControllerMethodStatsInterceptor(ControllerMethodCallStats stats) {
        this.stats = stats;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod hm) {
            stats.record(hm, request);
        }
        return true;
    }
}
