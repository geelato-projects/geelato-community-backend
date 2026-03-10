package cn.geelato.mcp.common.security;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class McpSecurityPropertiesTest {

    @Test
    void testDefaultValues() {
        McpSecurityProperties properties = new McpSecurityProperties();
        
        assertEquals("hybrid", properties.getAuthType());
        assertEquals("X-API-Key", properties.getApiKey().getHeaderName());
        assertTrue(properties.getApiKey().getKeys().isEmpty());
        assertTrue(properties.getApiKey().isEnabled());
        assertEquals("Authorization", properties.getJwt().getHeaderName());
        assertEquals("Bearer ", properties.getJwt().getPrefix());
        assertNull(properties.getJwt().getSignKey(), "JWT sign key should be null by default - must be set via environment variable");
        assertTrue(properties.getJwt().isEnabled());
    }

    @Test
    void testSetAuthType() {
        McpSecurityProperties properties = new McpSecurityProperties();
        
        properties.setAuthType("api-key");
        assertEquals("api-key", properties.getAuthType());
        
        properties.setAuthType("jwt");
        assertEquals("jwt", properties.getAuthType());
        
        properties.setAuthType("hybrid");
        assertEquals("hybrid", properties.getAuthType());
    }

    @Test
    void testSetApiKeyProperties() {
        McpSecurityProperties properties = new McpSecurityProperties();
        
        properties.getApiKey().setHeaderName("X-Custom-Key");
        properties.getApiKey().setKeys(Arrays.asList("key1", "key2", "key3"));
        properties.getApiKey().setEnabled(false);
        
        assertEquals("X-Custom-Key", properties.getApiKey().getHeaderName());
        assertEquals(3, properties.getApiKey().getKeys().size());
        assertFalse(properties.getApiKey().isEnabled());
    }

    @Test
    void testSetJwtProperties() {
        McpSecurityProperties properties = new McpSecurityProperties();
        
        properties.getJwt().setHeaderName("X-Token");
        properties.getJwt().setPrefix("Token ");
        properties.getJwt().setSignKey("custom-sign-key-12345");
        properties.getJwt().setEnabled(false);
        
        assertEquals("X-Token", properties.getJwt().getHeaderName());
        assertEquals("Token ", properties.getJwt().getPrefix());
        assertEquals("custom-sign-key-12345", properties.getJwt().getSignKey());
        assertFalse(properties.getJwt().isEnabled());
    }

    @Test
    void testApiKeyInnerClass() {
        McpSecurityProperties.ApiKey apiKey = new McpSecurityProperties.ApiKey();
        
        assertEquals("X-API-Key", apiKey.getHeaderName());
        assertTrue(apiKey.getKeys().isEmpty());
        assertTrue(apiKey.isEnabled());
        
        apiKey.setHeaderName("Test-Header");
        apiKey.setKeys(Arrays.asList("test-key"));
        apiKey.setEnabled(false);
        
        assertEquals("Test-Header", apiKey.getHeaderName());
        assertEquals(1, apiKey.getKeys().size());
        assertFalse(apiKey.isEnabled());
    }

    @Test
    void testJwtInnerClass() {
        McpSecurityProperties.Jwt jwt = new McpSecurityProperties.Jwt();
        
        assertEquals("Authorization", jwt.getHeaderName());
        assertEquals("Bearer ", jwt.getPrefix());
        assertNull(jwt.getSignKey(), "JWT sign key should be null by default");
        assertTrue(jwt.isEnabled());
        
        jwt.setHeaderName("Test-Auth");
        jwt.setPrefix("Test ");
        jwt.setSignKey("test-key");
        jwt.setEnabled(false);
        
        assertEquals("Test-Auth", jwt.getHeaderName());
        assertEquals("Test ", jwt.getPrefix());
        assertEquals("test-key", jwt.getSignKey());
        assertFalse(jwt.isEnabled());
    }
    
    @Test
    void testJwtSignKeyMustBeSetExplicitly() {
        McpSecurityProperties properties = new McpSecurityProperties();
        
        assertNull(properties.getJwt().getSignKey(), 
            "JWT sign key must be null by default for security reasons. " +
            "It should be set via MCP_JWT_SIGN_KEY environment variable.");
        
        String testKey = "secure-test-key-at-least-32-characters";
        properties.getJwt().setSignKey(testKey);
        assertEquals(testKey, properties.getJwt().getSignKey());
    }
}
