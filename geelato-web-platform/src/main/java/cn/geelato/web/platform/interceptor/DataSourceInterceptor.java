package cn.geelato.web.platform.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import cn.geelato.web.platform.boot.DynamicDatasourceHolder;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.HandlerInterceptor;

public class DataSourceInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) {
        String tenant=request.getHeader("tenant");
        String app=request.getHeader("app");
        DynamicDatasourceHolder.setDataSourceKey(app);
        return true;
    }
}
