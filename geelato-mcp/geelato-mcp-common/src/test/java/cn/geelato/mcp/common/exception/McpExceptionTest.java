package cn.geelato.mcp.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class McpExceptionTest {

    @Test
    void testExceptionWithMessage() {
        McpException exception = new McpException("Test error message");
        
        assertEquals("Test error message", exception.getMessage());
        assertNull(exception.getErrorCode());
    }

    @Test
    void testExceptionWithCodeAndMessage() {
        McpException exception = new McpException("ERR001", "Invalid parameter");
        
        assertEquals("Invalid parameter", exception.getMessage());
        assertEquals("ERR001", exception.getErrorCode());
    }

    @Test
    void testExceptionWithMessageAndCause() {
        Exception cause = new RuntimeException("Root cause");
        McpException exception = new McpException("Wrapper error", cause);
        
        assertEquals("Wrapper error", exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertNull(exception.getErrorCode());
    }

    @Test
    void testExceptionWithCodeMessageAndCause() {
        Exception cause = new RuntimeException("Root cause");
        McpException exception = new McpException("ERR002", "Database error", cause);
        
        assertEquals("Database error", exception.getMessage());
        assertEquals("ERR002", exception.getErrorCode());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testExceptionIsRuntimeException() {
        McpException exception = new McpException("Test");
        
        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    void testExceptionThrowAndCatch() {
        McpException caughtException = null;
        try {
            throw new McpException("ERR003", "Business error");
        } catch (McpException e) {
            caughtException = e;
        }
        
        assertNotNull(caughtException, "Exception should have been caught");
        assertEquals("ERR003", caughtException.getErrorCode());
        assertEquals("Business error", caughtException.getMessage());
    }
}
