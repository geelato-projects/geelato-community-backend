package cn.geelato.web.common.filter;

import cn.geelato.security.SecurityContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 统一在请求完成后清理 SecurityContext。
 * 安全主体必须在鉴权链路内部设置，不能在通用 Filter 中从请求直接注入。
 */
public class SecurityContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContext.clear();
        }
    }
}
