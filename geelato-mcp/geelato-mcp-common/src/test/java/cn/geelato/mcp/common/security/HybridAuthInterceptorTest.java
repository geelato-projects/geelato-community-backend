package cn.geelato.mcp.common.security;

import cn.geelato.mcp.common.service.ApiKeyValidationService;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HybridAuthInterceptorTest {
    
    private HybridAuthInterceptor hybridInterceptor;
    private ApiKeyAuthInterceptor apiKeyInterceptor;
    private JwtAuthInterceptor jwtInterceptor;
    
    private McpSecurityProperties properties;
    private ApiKeyValidationService validationService;
    private static final String TEST_SIGN_KEY = "test-jwt-sign-key-for-unit-tests-32ch";
    
    @BeforeEach
    void setUp() {
        properties = new McpSecurityProperties();
        properties.setAuthType("hybrid");
        properties.getApiKey().setEnabled(true);
        properties.getApiKey().setHeaderName("X-API-Key");
        properties.getApiKey().setKeys(Arrays.asList("valid-api-key"));
        properties.getJwt().setEnabled(true);
        properties.getJwt().setHeaderName("Authorization");
        properties.getJwt().setPrefix("Bearer ");
        properties.getJwt().setSignKey(TEST_SIGN_KEY);
        
        validationService = mock(ApiKeyValidationService.class);
        when(validationService.validate(anyString(), anyString())).thenReturn(false);
        
        apiKeyInterceptor = new ApiKeyAuthInterceptor(properties, validationService);
        jwtInterceptor = new JwtAuthInterceptor(properties);
        hybridInterceptor = new HybridAuthInterceptor(properties, apiKeyInterceptor, jwtInterceptor);
    }
    
    @Test
    void testValidApiKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "valid-api-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        boolean result = hybridInterceptor.preHandle(request, response, new Object());
        
        assertTrue(result, "Valid API key should pass hybrid authentication");
    }
    
    @Test
    void testValidJwtToken() throws Exception {
        String token = createValidToken("testuser");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        boolean result = hybridInterceptor.preHandle(request, response, new Object());
        
        assertTrue(result, "Valid JWT token should pass hybrid authentication");
    }
    
    @Test
    void testNoAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        boolean result = hybridInterceptor.preHandle(request, response, new Object());
        
        assertFalse(result, "No authentication should fail");
        assertEquals(401, response.getStatus());
    }
    
    @Test
    void testApiKeyOnlyMode() throws Exception {
        properties.setAuthType("api-key");
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "valid-api-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        boolean result = hybridInterceptor.preHandle(request, response, new Object());
        
        assertTrue(result, "API key should work in api-key mode");
    }
    
    @Test
    void testJwtOnlyMode() throws Exception {
        properties.setAuthType("jwt");
        
        String token = createValidToken("testuser");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        boolean result = hybridInterceptor.preHandle(request, response, new Object());
        
        assertTrue(result, "JWT token should work in jwt mode");
    }
    
    @Test
    void testBothValidCredentials() throws Exception {
        String token = createValidToken("testuser");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "valid-api-key");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        boolean result = hybridInterceptor.preHandle(request, response, new Object());
        
        assertTrue(result, "Both valid credentials should pass");
    }
    
    @Test
    void testInvalidApiKeyButValidJwt() throws Exception {
        String token = createValidToken("testuser");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "invalid-api-key");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        boolean result = hybridInterceptor.preHandle(request, response, new Object());
        
        assertTrue(result, "Valid JWT should pass even with invalid API key in hybrid mode");
    }
    
    @Test
    void testValidApiKeyButInvalidJwt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "valid-api-key");
        request.addHeader("Authorization", "Bearer invalid.token.here");
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        boolean result = hybridInterceptor.preHandle(request, response, new Object());
        
        assertTrue(result, "Valid API key should pass even with invalid JWT in hybrid mode");
    }
    
    @Test
    void testOptionsRequestAlwaysPasses() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("OPTIONS");
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        boolean result = hybridInterceptor.preHandle(request, response, new Object());
        
        assertTrue(result, "OPTIONS request should always pass");
    }
    
    @Test
    void testResponseFormatOnFailure() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        hybridInterceptor.preHandle(request, response, new Object());
        
        assertTrue(response.getContentType().contains("application/json"), 
            "Content-Type should contain application/json");
        assertTrue(response.getContentAsString().contains("unauthorized"));
    }
    
    @Test
    void testInvalidBothCredentials() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "invalid-api-key");
        request.addHeader("Authorization", "Bearer invalid.token.here");
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        boolean result = hybridInterceptor.preHandle(request, response, new Object());
        
        assertFalse(result, "Both invalid credentials should fail");
        assertEquals(401, response.getStatus());
    }
    
    private String createValidToken(String loginName) throws Exception {
        Map<String, String> claims = new HashMap<>();
        claims.put("loginName", loginName);
        return createToken(claims, 3600);
    }
    
    private String createToken(Map<String, String> claims, int expiresSeconds) throws Exception {
        Calendar instance = Calendar.getInstance();
        instance.add(Calendar.SECOND, expiresSeconds);
        
        com.auth0.jwt.JWTCreator.Builder tokenBuilder = JWT.create();
        for (Map.Entry<String, String> entry : claims.entrySet()) {
            tokenBuilder.withClaim(entry.getKey(), entry.getValue());
        }
        
        String token = tokenBuilder
                .withExpiresAt(instance.getTime())
                .sign(Algorithm.HMAC256(TEST_SIGN_KEY));
        
        return confoundPayload(token);
    }
    
    private String confoundPayload(String token) {
        String[] split = token.split("\\.");
        if (split.length != 3) {
            return token;
        }
        String payload = split[1];
        int length = payload.length() / 2;
        int index = payload.length() % 2 != 0 ? length + 1 : length;
        return split[0] + "." + reversePayload(payload, index) + "." + split[2];
    }
    
    private String reversePayload(String payload, int index) {
        return payload.substring(index) + payload.substring(0, index);
    }
}
