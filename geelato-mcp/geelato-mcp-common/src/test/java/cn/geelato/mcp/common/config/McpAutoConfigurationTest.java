package cn.geelato.mcp.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

import static org.junit.jupiter.api.Assertions.*;

class McpAutoConfigurationTest {

    @Test
    void testAutoConfigurationAnnotations() {
        Class<McpAutoConfiguration> clazz = McpAutoConfiguration.class;

        assertTrue(clazz.isAnnotationPresent(AutoConfiguration.class));
        assertTrue(clazz.isAnnotationPresent(ComponentScan.class));
        assertTrue(clazz.isAnnotationPresent(EnableConfigurationProperties.class));
    }

    @Test
    void testComponentScanConfiguration() {
        ComponentScan componentScan = McpAutoConfiguration.class.getAnnotation(ComponentScan.class);
        assertNotNull(componentScan);
        assertArrayEquals(new String[]{"cn.geelato.mcp"}, componentScan.basePackages());
    }

    @Test
    void testEnableConfigurationProperties() {
        EnableConfigurationProperties enableConfig = McpAutoConfiguration.class.getAnnotation(EnableConfigurationProperties.class);
        assertNotNull(enableConfig);
        Class<?>[] value = enableConfig.value();
        assertEquals(1, value.length);
        assertEquals(McpProperties.class, value[0]);
    }

    @Test
    void testConfigurationClassInstantiation() {
        McpAutoConfiguration config = new McpAutoConfiguration();
        assertNotNull(config);
    }
}
