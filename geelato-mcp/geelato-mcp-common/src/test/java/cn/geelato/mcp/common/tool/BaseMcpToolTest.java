package cn.geelato.mcp.common.tool;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class BaseMcpToolTest {

    // 创建一个具体的测试类来测试抽象类
    static class TestMcpTool extends BaseMcpTool {
        public void testLogExecution(String toolName, Object... params) {
            logToolExecution(toolName, params);
        }

        public void testLogResult(String toolName, Object result) {
            logToolResult(toolName, result);
        }

        public void testLogError(String toolName, Exception e) {
            logToolError(toolName, e);
        }

        public Logger getLogger() {
            return logger;
        }
    }

    @Test
    void testLoggerInitialization() throws Exception {
        TestMcpTool tool = new TestMcpTool();

        Field loggerField = BaseMcpTool.class.getDeclaredField("logger");
        loggerField.setAccessible(true);
        Logger logger = (Logger) loggerField.get(tool);

        assertNotNull(logger);
        assertEquals("cn.geelato.mcp.common.tool.BaseMcpToolTest$TestMcpTool", logger.getName());
    }

    @Test
    void testLogToolExecution() {
        TestMcpTool tool = new TestMcpTool();
        // 验证方法可以正常调用，不抛出异常
        assertDoesNotThrow(() -> tool.testLogExecution("testTool", "param1", "param2"));
    }

    @Test
    void testLogToolResult() {
        TestMcpTool tool = new TestMcpTool();
        // 验证方法可以正常调用，不抛出异常
        assertDoesNotThrow(() -> tool.testLogResult("testTool", "result data"));
    }

    @Test
    void testLogToolError() {
        TestMcpTool tool = new TestMcpTool();
        Exception testException = new RuntimeException("Test error");
        // 验证方法可以正常调用，不抛出异常
        assertDoesNotThrow(() -> tool.testLogError("testTool", testException));
    }

    @Test
    void testLogToolResultWithNull() {
        TestMcpTool tool = new TestMcpTool();
        // 验证处理 null 结果不抛出异常
        assertDoesNotThrow(() -> tool.testLogResult("testTool", null));
    }

    @Test
    void testLogToolExecutionWithNoParams() {
        TestMcpTool tool = new TestMcpTool();
        // 验证无参数调用不抛出异常
        assertDoesNotThrow(() -> tool.testLogExecution("testTool"));
    }

    @Test
    void testLogToolExecutionWithMultipleParams() {
        TestMcpTool tool = new TestMcpTool();
        // 验证多参数调用不抛出异常
        assertDoesNotThrow(() -> tool.testLogExecution("testTool",
                "param1", 123, true, null, new Object()));
    }
}
