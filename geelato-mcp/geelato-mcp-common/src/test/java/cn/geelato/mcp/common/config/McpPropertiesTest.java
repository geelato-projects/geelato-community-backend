package cn.geelato.mcp.common.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class McpPropertiesTest {

    @Test
    void testDefaultValues() {
        McpProperties properties = new McpProperties();
        
        assertTrue(properties.isEnabled());
        assertEquals("geelato-mcp-server", properties.getServerName());
        assertEquals("1.0.0", properties.getServerVersion());
        assertEquals(30000, properties.getTimeout());
        assertEquals("Geelato MCP Server", properties.getDescription());
    }

    @Test
    void testSetEnabled() {
        McpProperties properties = new McpProperties();
        
        properties.setEnabled(false);
        assertFalse(properties.isEnabled());
        
        properties.setEnabled(true);
        assertTrue(properties.isEnabled());
    }

    @Test
    void testSetServerName() {
        McpProperties properties = new McpProperties();
        
        properties.setServerName("custom-mcp-server");
        assertEquals("custom-mcp-server", properties.getServerName());
    }

    @Test
    void testSetServerVersion() {
        McpProperties properties = new McpProperties();
        
        properties.setServerVersion("2.0.0");
        assertEquals("2.0.0", properties.getServerVersion());
    }

    @Test
    void testSetTimeout() {
        McpProperties properties = new McpProperties();
        
        properties.setTimeout(60000);
        assertEquals(60000, properties.getTimeout());
    }

    @Test
    void testSetDescription() {
        McpProperties properties = new McpProperties();
        
        properties.setDescription("Custom MCP Server Description");
        assertEquals("Custom MCP Server Description", properties.getDescription());
    }

    @Test
    void testAllSettersAndGetters() {
        McpProperties properties = new McpProperties();
        
        properties.setEnabled(false);
        properties.setServerName("test-server");
        properties.setServerVersion("3.0.0");
        properties.setTimeout(120000);
        properties.setDescription("Test Description");
        
        assertFalse(properties.isEnabled());
        assertEquals("test-server", properties.getServerName());
        assertEquals("3.0.0", properties.getServerVersion());
        assertEquals(120000, properties.getTimeout());
        assertEquals("Test Description", properties.getDescription());
    }
}
