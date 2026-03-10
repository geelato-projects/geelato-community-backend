package cn.geelato.mcp.common.security;

import cn.geelato.mcp.common.service.ApiKeyValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiKeyAuthInterceptorTest {
    
    private ApiKeyAuthInterceptor interceptor;
    private MockHttpServletResponse response;
    private McpSecurityProperties properties;
    private ApiKeyValidationService validationService;
    
    @BeforeEach
    void setUp() {
        properties = new McpSecurityProperties();
        properties.getApiKey().setEnabled(true);
        properties.getApiKey().setHeaderName("X-API-Key");
        properties.getApiKey().setKeys(Arrays.asList("valid-key-1", "valid-key-2"));
        
        validationService = mock(ApiKeyValidationService.class);
        when(validationService.validate(anyString(), anyString())).thenReturn(false);
        
        interceptor = new ApiKeyAuthInterceptor(properties, validationService);
        response = new MockHttpServletResponse();
    }
    
    @Test
    void testValidApiKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "valid-key-1");
        
        boolean result = interceptor.preHandle(request, response, new Object());
        
        assertTrue(result, "Valid API key should pass authentication");
        assertEquals(200, response.getStatus());
    }
    
    @Test
    void testValidApiKeySecondKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "valid-key-2");
        
        boolean result = interceptor.preHandle(request, response, new Object());
        
        assertTrue(result, "Second valid API key should pass authentication");
        assertEquals(200, response.getStatus());
    }
    
    @Test
    void testInvalidApiKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "invalid-key");
        
        boolean result = interceptor.preHandle(request, response, new Object());
        
        assertFalse(result, "Invalid API key should fail authentication");
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("invalid_api_key"));
    }
    
    @Test
    void testMissingApiKeyInHybridMode() throws Exception {
        properties.setAuthType("hybrid");
        MockHttpServletRequest request = new MockHttpServletRequest();
        
        boolean result = interceptor.preHandle(request, response, new Object());
        
        assertTrue(result, "Missing API key in hybrid mode should be skipped");
    }
    
    @Test
    void testMissingApiKeyInApiKeyOnlyMode() throws Exception {
        properties.setAuthType("api-key");
        MockHttpServletRequest request = new MockHttpServletRequest();
        
        boolean result = interceptor.preHandle(request, response, new Object());
        
        assertFalse(result, "Missing API key in api-key only mode should be rejected");
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("missing_api_key"));
    }
    
    @Test
    void testDisabledApiKeyAuth() throws Exception {
        properties.getApiKey().setEnabled(false);
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "any-key");
        
        boolean result = interceptor.preHandle(request, response, new Object());
        
        assertTrue(result, "When API Key auth is disabled, should always pass");
    }
    
    @Test
    void testEmptyApiKey() throws Exception {
        properties.setAuthType("api-key");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "");
        
        boolean result = interceptor.preHandle(request, response, new Object());
        
        assertFalse(result, "Empty API key should fail");
        assertEquals(401, response.getStatus());
    }
    
    @Test
    void testCustomHeaderName() throws Exception {
        properties.getApiKey().setHeaderName("X-Custom-Auth");
        properties.getApiKey().setKeys(Arrays.asList("custom-key"));
        interceptor = new ApiKeyAuthInterceptor(properties, validationService);
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Custom-Auth", "custom-key");
        
        boolean result = interceptor.preHandle(request, response, new Object());
        
        assertTrue(result, "Custom header name should work");
    }
    
    @Test
    void testOptionsRequestAlwaysPasses() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("OPTIONS");
        
        boolean result = interceptor.preHandle(request, response, new Object());
        
        assertTrue(result, "OPTIONS request should always pass");
    }
    
    @Test
    void testNoKeysConfigured() throws Exception {
        properties.setAuthType("api-key");
        properties.getApiKey().setKeys(Collections.emptyList());
        interceptor = new ApiKeyAuthInterceptor(properties, validationService);
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "any-key");
        
        boolean result = interceptor.preHandle(request, response, new Object());
        
        assertFalse(result, "No keys configured should reject all requests");
    }
    
    @Test
    void testCaseSensitiveApiKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "VALID-KEY-1");
        
        boolean result = interceptor.preHandle(request, response, new Object());
        
        assertFalse(result, "API key should be case sensitive");
        assertEquals(401, response.getStatus());
    }
    
    @Test
    void testResponseContentType() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "invalid-key");
        
        interceptor.preHandle(request, response, new Object());
        
        assertTrue(response.getContentType().contains("application/json"), 
            "Content-Type should contain application/json");
        assertTrue(response.getCharacterEncoding().equalsIgnoreCase("UTF-8"),
            "Character encoding should be UTF-8");
    }
}
