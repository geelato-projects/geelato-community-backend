package cn.geelato.mcp.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@ComponentScan(basePackages = "cn.geelato.mcp")
@EnableConfigurationProperties(McpProperties.class)
public class McpAutoConfiguration {

}
