package cn.geelato.mcp.common.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseMcpTool {
    
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    
    protected void logToolExecution(String toolName, Object... params) {
        logger.info("执行MCP工具: {}, 参数: {}", toolName, params);
    }
    
    protected void logToolResult(String toolName, Object result) {
        logger.info("MCP工具执行完成: {}, 结果类型: {}", toolName, result != null ? result.getClass().getSimpleName() : "null");
    }
    
    protected void logToolError(String toolName, Exception e) {
        logger.error("MCP工具执行失败: {}", toolName, e);
    }
}
