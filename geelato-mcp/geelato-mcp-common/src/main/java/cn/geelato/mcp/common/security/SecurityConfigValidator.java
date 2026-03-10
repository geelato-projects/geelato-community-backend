package cn.geelato.mcp.common.security;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityConfigValidator {
    
    private static final String LEGACY_DEFAULT_KEY = "5A1332068BA9FD17";
    
    private final McpSecurityProperties securityProperties;
    
    @PostConstruct
    public void validateSecurityConfig() {
        String authType = securityProperties.getAuthType();
        
        if ("jwt".equals(authType) || "hybrid".equals(authType)) {
            validateJwtConfig();
        }
        
        if ("api-key".equals(authType) || "hybrid".equals(authType)) {
            validateApiKeyConfig();
        }
        
        log.info("Security configuration validated successfully. Auth type: {}", authType);
    }
    
    private void validateJwtConfig() {
        String signKey = securityProperties.getJwt().getSignKey();
        
        if (!StringUtils.hasText(signKey)) {
            throw new IllegalStateException(
                "JWT sign key is not configured! " +
                "Please set the MCP_JWT_SIGN_KEY environment variable. " +
                "Example: export MCP_JWT_SIGN_KEY=\"your-secure-key-at-least-32-characters\"");
        }
        
        if (LEGACY_DEFAULT_KEY.equals(signKey)) {
            throw new IllegalStateException(
                "SECURITY RISK: Using legacy default JWT sign key is not allowed! " +
                "Please set a unique MCP_JWT_SIGN_KEY environment variable for production.");
        }
        
        if (signKey.length() < 16) {
            log.warn("WARNING: JWT sign key is shorter than 16 characters. " +
                    "Consider using a longer key for better security.");
        }
        
        log.info("JWT configuration validated. Key length: {} characters", signKey.length());
    }
    
    private void validateApiKeyConfig() {
        if (!securityProperties.getApiKey().isEnabled()) {
            return;
        }
        
        if (!securityProperties.getApiKey().isUseDatabase()) {
            if (securityProperties.getApiKey().getKeys() == null || 
                securityProperties.getApiKey().getKeys().isEmpty()) {
                throw new IllegalStateException(
                    "API Key authentication is enabled but no keys are configured! " +
                    "Please set MCP_API_KEY_1 and MCP_API_KEY_2 environment variables, " +
                    "or enable database validation with MCP_API_KEY_USE_DATABASE=true");
            }
        }
        
        log.info("API Key configuration validated. Keys configured: {}, Use database: {}", 
                securityProperties.getApiKey().getKeys() != null ? 
                    securityProperties.getApiKey().getKeys().size() : 0,
                securityProperties.getApiKey().isUseDatabase());
    }
}
