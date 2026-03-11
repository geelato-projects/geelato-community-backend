package cn.geelato.mcp.fms.config;

import cn.geelato.mcp.fms.tool.ContainerQueryTool;
import cn.geelato.mcp.fms.tool.OrderQueryTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpFmsConfiguration {

    @Bean
    public ToolCallbackProvider fmsTools(ContainerQueryTool containerQueryTool, OrderQueryTool orderQueryTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(containerQueryTool, orderQueryTool)
                .build();
    }
}
