package cn.geelato.mcp.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class McpSecurityConfig implements WebMvcConfigurer {
    
    private final McpSecurityProperties securityProperties;
    private final ApiKeyAuthInterceptor apiKeyAuthInterceptor;
    private final JwtAuthInterceptor jwtAuthInterceptor;
    private final HybridAuthInterceptor hybridAuthInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 根据认证类型注册对应的拦截器
        String authType = securityProperties.getAuthType();
        
        switch (authType) {
            case "api-key":
                // 仅注册 API Key 拦截器
                // 排除 SSE 端点，让 Spring AI MCP 自动配置的端点可以正常工作
                registry.addInterceptor(apiKeyAuthInterceptor)
                        .addPathPatterns("/mcp/**", "/api/**")
                        .excludePathPatterns("/mcp/**/message")
                        .order(1);
                break;
                
            case "jwt":
                // 仅注册 JWT 拦截器
                registry.addInterceptor(jwtAuthInterceptor)
                        .addPathPatterns("/mcp/**", "/api/**")
                        .excludePathPatterns("/mcp/**/message")
                        .order(1);
                break;
                
            case "hybrid":
            default:
                // 注册混合认证拦截器
                registry.addInterceptor(hybridAuthInterceptor)
                        .addPathPatterns("/mcp/**", "/api/**")
                        .excludePathPatterns("/mcp/**/message")
                        .order(1);
                break;
        }
    }
}
