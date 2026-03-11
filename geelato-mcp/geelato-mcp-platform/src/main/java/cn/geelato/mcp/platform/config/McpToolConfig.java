package cn.geelato.mcp.platform.config;

import cn.geelato.mcp.platform.tool.dict.DictQueryTool;
import cn.geelato.mcp.platform.tool.meta.MetaModelTool;
import cn.geelato.mcp.platform.tool.page.PageConfigTool;
import cn.geelato.mcp.platform.tool.system.SystemInfoTool;
import cn.geelato.mcp.platform.tool.user.UserQueryTool;
import cn.geelato.mcp.platform.tool.view.ViewQueryTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 工具配置类
 * 注册所有 MCP 工具到 Spring AI MCP Server
 */
@Configuration
public class McpToolConfig {

    /**
     * 注册页面配置工具
     */
    @Bean
    public ToolCallbackProvider pageConfigToolProvider(PageConfigTool pageConfigTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(pageConfigTool)
                .build();
    }

    /**
     * 注册元数据模型工具
     */
    @Bean
    public ToolCallbackProvider metaModelToolProvider(MetaModelTool metaModelTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(metaModelTool)
                .build();
    }

    /**
     * 注册视图查询工具
     */
    @Bean
    public ToolCallbackProvider viewQueryToolProvider(ViewQueryTool viewQueryTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(viewQueryTool)
                .build();
    }

    /**
     * 注册数据字典工具
     */
    @Bean
    public ToolCallbackProvider dictQueryToolProvider(DictQueryTool dictQueryTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(dictQueryTool)
                .build();
    }

    /**
     * 注册用户查询工具
     */
    @Bean
    public ToolCallbackProvider userQueryToolProvider(UserQueryTool userQueryTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(userQueryTool)
                .build();
    }

    /**
     * 注册系统信息工具
     */
    @Bean
    public ToolCallbackProvider systemInfoToolProvider(SystemInfoTool systemInfoTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(systemInfoTool)
                .build();
    }
}
