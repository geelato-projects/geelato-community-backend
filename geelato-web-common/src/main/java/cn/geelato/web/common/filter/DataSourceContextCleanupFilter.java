package cn.geelato.web.common.filter;

import cn.geelato.datasource.DataSourceOverrideContext;
import cn.geelato.datasource.DynamicDataSourceHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 数据源上下文请求级清理安全网：请求结束时清空路由 ThreadLocal
 * （{@link DynamicDataSourceHolder} 与 {@link DataSourceOverrideContext}），
 * 兜住业务层任何未恢复的裸写切换，防止池化线程把路由 key 串号到下一请求、
 * 以及泄漏 key 压过请求级数据源覆盖。
 * <p>注册在最外层（先于解密0/安全1/缓存2/审计3/实验4/归档查询5），
 * 其 finally 覆盖整个请求链路。
 */
public class DataSourceContextCleanupFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            DynamicDataSourceHolder.clearDataSourceKey();
            DataSourceOverrideContext.clear();
        }
    }
}
