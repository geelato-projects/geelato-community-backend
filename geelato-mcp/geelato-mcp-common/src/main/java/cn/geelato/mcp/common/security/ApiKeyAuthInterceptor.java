package cn.geelato.mcp.common.security;

import cn.geelato.mcp.common.service.ApiKeyValidationService;
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
public class ApiKeyAuthInterceptor implements HandlerInterceptor {
    
    private final McpSecurityProperties securityProperties;
    private final ApiKeyValidationService apiKeyValidationService;
    
    @Override
    public boolean preHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler) throws Exception {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (!securityProperties.getApiKey().isEnabled()) {
            return true;
        }
        
        String headerName = securityProperties.getApiKey().getHeaderName();
        String rawApiKey = request.getHeader(headerName);
        
        if (!StringUtils.hasText(rawApiKey)) {
            if ("api-key".equals(securityProperties.getAuthType())) {
                log.warn("API Key missing in api-key auth mode");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"error\": \"missing_api_key\", \"message\": \"认证失败：请提供有效的 API Key\"}");
                return false;
            }
            return true;
        }
        
        String clientIp = getClientIp(request);
        
        if (securityProperties.getApiKey().isUseDatabase()) {
            if (apiKeyValidationService != null && apiKeyValidationService.validate(rawApiKey, clientIp)) {
                apiKeyValidationService.recordUsage(rawApiKey);
                log.info("API Key authentication successful");
                return true;
            }
        } else {
            if (securityProperties.getApiKey().getKeys().contains(rawApiKey)) {
                log.info("API Key authentication successful");
                return true;
            }
        }
        
        log.warn("Invalid API Key: {}", rawApiKey);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\": \"invalid_api_key\", \"message\": \"认证失败：无效的 API Key\"}");
        return false;
    }
    
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
