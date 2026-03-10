package cn.geelato.mcp.common.security;

import cn.geelato.mcp.common.service.ApiKeyValidationService;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class McpSecurityConfigTest {

    @Test
    void testSecurityConfigCreation() {
        McpSecurityProperties properties = new McpSecurityProperties();
        properties.getJwt().setSignKey("test-jwt-sign-key-for-unit-tests-32ch");
        ApiKeyValidationService validationService = mock(ApiKeyValidationService.class);
        
        ApiKeyAuthInterceptor apiKeyInterceptor = new ApiKeyAuthInterceptor(properties, validationService);
        JwtAuthInterceptor jwtInterceptor = new JwtAuthInterceptor(properties);
        HybridAuthInterceptor hybridInterceptor = new HybridAuthInterceptor(properties, apiKeyInterceptor, jwtInterceptor);

        McpSecurityConfig config = new McpSecurityConfig(properties, apiKeyInterceptor, jwtInterceptor, hybridInterceptor);

        assertNotNull(config);
    }

    @Test
    void testAddInterceptorsWithHybridAuth() {
        McpSecurityProperties properties = new McpSecurityProperties();
        properties.setAuthType("hybrid");
        properties.getJwt().setSignKey("test-jwt-sign-key-for-unit-tests-32ch");
        ApiKeyValidationService validationService = mock(ApiKeyValidationService.class);

        ApiKeyAuthInterceptor apiKeyInterceptor = new ApiKeyAuthInterceptor(properties, validationService);
        JwtAuthInterceptor jwtInterceptor = new JwtAuthInterceptor(properties);
        HybridAuthInterceptor hybridInterceptor = new HybridAuthInterceptor(properties, apiKeyInterceptor, jwtInterceptor);

        McpSecurityConfig config = new McpSecurityConfig(properties, apiKeyInterceptor, jwtInterceptor, hybridInterceptor);

        InterceptorRegistry registry = mock(InterceptorRegistry.class);
        InterceptorRegistration registration = mock(InterceptorRegistration.class);

        when(registry.addInterceptor(any())).thenReturn(registration);
        when(registration.addPathPatterns(anyString(), anyString())).thenReturn(registration);
        when(registration.excludePathPatterns(anyString())).thenReturn(registration);
        when(registration.order(anyInt())).thenReturn(registration);

        config.addInterceptors(registry);

        verify(registry).addInterceptor(hybridInterceptor);
        verify(registration).addPathPatterns("/mcp/**", "/api/**");
        verify(registration).excludePathPatterns("/mcp/**/message");
        verify(registration).order(1);
    }

    @Test
    void testAddInterceptorsWithApiKeyAuth() {
        McpSecurityProperties properties = new McpSecurityProperties();
        properties.setAuthType("api-key");
        properties.getJwt().setSignKey("test-jwt-sign-key-for-unit-tests-32ch");
        ApiKeyValidationService validationService = mock(ApiKeyValidationService.class);

        ApiKeyAuthInterceptor apiKeyInterceptor = new ApiKeyAuthInterceptor(properties, validationService);
        JwtAuthInterceptor jwtInterceptor = new JwtAuthInterceptor(properties);
        HybridAuthInterceptor hybridInterceptor = new HybridAuthInterceptor(properties, apiKeyInterceptor, jwtInterceptor);

        McpSecurityConfig config = new McpSecurityConfig(properties, apiKeyInterceptor, jwtInterceptor, hybridInterceptor);

        InterceptorRegistry registry = mock(InterceptorRegistry.class);
        InterceptorRegistration registration = mock(InterceptorRegistration.class);

        when(registry.addInterceptor(any())).thenReturn(registration);
        when(registration.addPathPatterns(anyString(), anyString())).thenReturn(registration);
        when(registration.excludePathPatterns(anyString())).thenReturn(registration);
        when(registration.order(anyInt())).thenReturn(registration);

        config.addInterceptors(registry);

        verify(registry).addInterceptor(apiKeyInterceptor);
        verify(registration).addPathPatterns("/mcp/**", "/api/**");
        verify(registration).excludePathPatterns("/mcp/**/message");
        verify(registration).order(1);
    }

    @Test
    void testAddInterceptorsWithJwtAuth() {
        McpSecurityProperties properties = new McpSecurityProperties();
        properties.setAuthType("jwt");
        properties.getJwt().setSignKey("test-jwt-sign-key-for-unit-tests-32ch");
        ApiKeyValidationService validationService = mock(ApiKeyValidationService.class);

        ApiKeyAuthInterceptor apiKeyInterceptor = new ApiKeyAuthInterceptor(properties, validationService);
        JwtAuthInterceptor jwtInterceptor = new JwtAuthInterceptor(properties);
        HybridAuthInterceptor hybridInterceptor = new HybridAuthInterceptor(properties, apiKeyInterceptor, jwtInterceptor);

        McpSecurityConfig config = new McpSecurityConfig(properties, apiKeyInterceptor, jwtInterceptor, hybridInterceptor);

        InterceptorRegistry registry = mock(InterceptorRegistry.class);
        InterceptorRegistration registration = mock(InterceptorRegistration.class);

        when(registry.addInterceptor(any())).thenReturn(registration);
        when(registration.addPathPatterns(anyString(), anyString())).thenReturn(registration);
        when(registration.excludePathPatterns(anyString())).thenReturn(registration);
        when(registration.order(anyInt())).thenReturn(registration);

        config.addInterceptors(registry);

        verify(registry).addInterceptor(jwtInterceptor);
        verify(registration).addPathPatterns("/mcp/**", "/api/**");
        verify(registration).excludePathPatterns("/mcp/**/message");
        verify(registration).order(1);
    }
}
