package cn.geelato.mcp.common.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {
    
    private final McpSecurityProperties securityProperties;
    
    @Override
    public boolean preHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler) throws Exception {

        // 允许 CORS 预检请求通过
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 如果 JWT 认证未启用，直接跳过
        if (!securityProperties.getJwt().isEnabled()) {
            return true;
        }
        
        String headerName = securityProperties.getJwt().getHeaderName();
        String prefix = securityProperties.getJwt().getPrefix();
        String rawToken = request.getHeader(headerName);
        
        // 如果没有 Token
        if (!StringUtils.hasText(rawToken)) {
            // 在单认证模式(jwt)下，缺失凭证必须拒绝
            if ("jwt".equals(securityProperties.getAuthType())) {
                log.warn("JWT Token missing in jwt auth mode");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"error\": \"missing_token\", \"message\": \"认证失败：请提供有效的 JWT Token\"}");
                return false;
            }
            // 在混合模式下，跳过此认证让其他拦截器处理
            return true;
        }
        
        // 移除前缀
        String token = rawToken.replace(prefix, "");
        
        // 验证 JWT Token (使用配置的签名密钥)
        try {
            String signKey = securityProperties.getJwt().getSignKey();
            DecodedJWT verify = JwtUtil.verify(token, signKey);
            log.info("JWT authentication successful for user: {}", 
                    verify.getClaim("loginName").asString());
            return true;
        } catch (Exception e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\": \"invalid_token\", \"message\": \"无效的 JWT Token\"}");
            return false;
        }
    }
}
