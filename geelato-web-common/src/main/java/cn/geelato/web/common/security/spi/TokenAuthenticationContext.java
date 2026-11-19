package cn.geelato.web.common.security.spi;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.method.HandlerMethod;

/**
 * 单次认证的输入上下文。{@code handlerMethod} 由拦截器保证非空
 * （非 HandlerMethod 的请求在进入认证前已放行）。
 */
public record TokenAuthenticationContext(String rawToken, HttpServletRequest request, HandlerMethod handlerMethod) {

    /** 去掉 scheme 前缀后的凭证主体（前缀已由 {@code supports} 保证存在）。 */
    public String stripped(String prefix) {
        return rawToken.substring(prefix.length());
    }
}
