package cn.geelato.mcp.common.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthInterceptorTest {
    
    private JwtAuthInterceptor interceptor;
    private MockHttpServletResponse response;
    private McpSecurityProperties properties;
    private static final String TEST_SIGN_KEY = "5A1332068BA9FD17";
    
    @BeforeEach
    void setUp() {
        properties = new McpSecurityProperties();
        properties.getJwt().setEnabled(true);
        properties.getJwt().setHeaderName("Authorization");
        properties.getJwt().setPrefix("Bearer ");
        properties.getJwt().setSignKey(TEST_SIGN_KEY);
        
        interceptor = new JwtAuthInterceptor(properties);
        response = new MockHttpServletResponse();
    }
    
    @Test
    void testValidJwtToken() throws Exception {
        String token = createValidToken("testuser");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        
        boolean result = interceptor.preHandle(request, response, new Object());
        
        assertTrue(result, "Valid JWT token should pass authentication");
        assertEquals(200, response.getStatus());
    }
    
    @Test
    void testInvalidJwtToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid.token.here");
        
        boolean result = interceptor.preHandle(request, response, new Object());
        
        assertFalse(result, "Invalid JWT token should fail authentication");
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("invalid_token"));
    }
    
    @Test
    void testMissingTokenInHybridMode() throws Exception {
        properties.setAuthType("hybrid");
        MockHttpServletRequest request = new MockHttpServletRequest();
        
        boolean result = interceptor.preHandle(request, response, new Object());
        
        assertTrue(result, "Missing token in hybrid mode should be skipped");
    }
    
    @Test
    void testMissingTokenInJwtOnlyMode() throws Exception {
        properties.setAuthType("jwt");
        MockHttpServletRequest request = new MockHttpServletRequest();
        
        boolean result = interceptor.preHandle(request, response, new Object());
        
        assertFalse(result, "Missing token in jwt only mode should be rejected");
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("missing_token"));
    }
    
    @Test
    void testDisabledJwtAuth() throws Exception {
        properties.getJwt().setEnabled(false);
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer any-token");
        
        boolean result = interceptor.preHandle(request, response, new Object());
        
        assertTrue(result, "When JWT auth is disabled, should always pass");
    }
    
    @Test
    void testTokenWithWrongSignKey() throws Exception {
        String token = createTokenWithKey("wrong-key-12345678", "testuser");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        
        boolean result = interceptor.preHandle(request, response, new Object());
        
        assertFalse(result, "Token signed with wrong key should fail");
        assertEquals(401, response.getStatus());
    }
    
    @Test
    void testExpiredToken() throws Exception {
        String token = createExpiredToken("testuser");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        
        boolean result = interceptor.preHandle(request, response, new Object());
        
        assertFalse(result, "Expired token should fail");
        assertEquals(401, response.getStatus());
    }
    
    @Test
    void testTokenWithoutPrefix() throws Exception {
        String token = createValidToken("testuser");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", token);
        
        boolean result = interceptor.preHandle(request, response, new Object());
        
        assertTrue(result, "Token without prefix should still work (prefix is removed)");
    }
    
    private String createValidToken(String loginName) throws Exception {
        Map<String, String> claims = new HashMap<>();
        claims.put("loginName", loginName);
        return createToken(claims, 3600);
    }
    
    private String createExpiredToken(String loginName) throws Exception {
        Map<String, String> claims = new HashMap<>();
        claims.put("loginName", loginName);
        return createToken(claims, -3600);
    }
    
    private String createTokenWithKey(String signKey, String loginName) throws Exception {
        Calendar instance = Calendar.getInstance();
        instance.add(Calendar.SECOND, 3600);
        
        String token = JWT.create()
                .withClaim("loginName", loginName)
                .withExpiresAt(instance.getTime())
                .sign(Algorithm.HMAC256(signKey));
        
        return confoundPayload(token);
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
