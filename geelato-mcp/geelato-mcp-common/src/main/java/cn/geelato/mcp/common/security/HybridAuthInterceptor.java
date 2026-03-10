package cn.geelato.mcp.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class HybridAuthInterceptor implements HandlerInterceptor {
    
    private final McpSecurityProperties securityProperties;
    private final ApiKeyAuthInterceptor apiKeyAuthInterceptor;
    private final JwtAuthInterceptor jwtAuthInterceptor;
    
    @Override
    public boolean preHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler) throws Exception {

        // 允许 CORS 预检请求通过
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authType = securityProperties.getAuthType();

        log.debug("Processing authentication with type: {}", authType);

        // 根据认证类型选择认证策略
        switch (authType) {
            case "api-key":
                // 仅 API Key 认证
                return apiKeyAuthInterceptor.preHandle(request, response, handler);

            case "jwt":
                // 仅 JWT 认证
                return jwtAuthInterceptor.preHandle(request, response, handler);

            case "hybrid":
            default:
                // 混合模式：任一认证通过即可
                return tryApiKeyAuth(request, response, handler) ||
                       tryJwtAuth(request, response, handler) ||
                       failAuthentication(response);
        }
    }
    
    /**
     * 尝试 API Key 认证
     */
    private boolean tryApiKeyAuth(HttpServletRequest request, 
                                 HttpServletResponse response, 
                                 Object handler) throws Exception {
        // 检查是否有 API Key
        String apiKey = request.getHeader(securityProperties.getApiKey().getHeaderName());
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return false;
        }
        
        return apiKeyAuthInterceptor.preHandle(request, response, handler);
    }
    
    /**
     * 尝试 JWT 认证
     */
    private boolean tryJwtAuth(HttpServletRequest request, 
                              HttpServletResponse response, 
                              Object handler) throws Exception {
        // 检查是否有 JWT Token
        String token = request.getHeader(securityProperties.getJwt().getHeaderName());
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        
        return jwtAuthInterceptor.preHandle(request, response, handler);
    }
    
    /**
     * 认证失败处理
     */
    private boolean failAuthentication(HttpServletResponse response) throws Exception {
        log.warn("Authentication failed: No valid API Key or JWT Token provided");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\": \"unauthorized\", \"message\": \"认证失败：请提供有效的 API Key 或 JWT Token\"}");
        return false;
    }
}
