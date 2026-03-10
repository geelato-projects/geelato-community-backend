package cn.geelato.mcp.common.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class McpResultTest {

    @Test
    void testSuccessWithData() {
        McpResult<String> result = McpResult.success("test data");
        
        assertTrue(result.isSuccess());
        assertEquals("test data", result.getData());
        assertNull(result.getMessage());
        assertNull(result.getErrorCode());
    }

    @Test
    void testSuccessWithDataAndMessage() {
        McpResult<String> result = McpResult.success("test data", "Operation successful");
        
        assertTrue(result.isSuccess());
        assertEquals("test data", result.getData());
        assertEquals("Operation successful", result.getMessage());
        assertNull(result.getErrorCode());
    }

    @Test
    void testErrorWithMessage() {
        McpResult<String> result = McpResult.error("Something went wrong");
        
        assertFalse(result.isSuccess());
        assertNull(result.getData());
        assertEquals("Something went wrong", result.getMessage());
        assertNull(result.getErrorCode());
    }

    @Test
    void testErrorWithCodeAndMessage() {
        McpResult<String> result = McpResult.error("ERR001", "Invalid parameter");
        
        assertFalse(result.isSuccess());
        assertNull(result.getData());
        assertEquals("Invalid parameter", result.getMessage());
        assertEquals("ERR001", result.getErrorCode());
    }

    @Test
    void testSuccessWithNullData() {
        McpResult<Object> result = McpResult.success(null);
        
        assertTrue(result.isSuccess());
        assertNull(result.getData());
    }

    @Test
    void testSuccessWithComplexData() {
        TestUser user = new TestUser("U001", "testuser", "test@example.com");
        McpResult<TestUser> result = McpResult.success(user);
        
        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals("U001", result.getData().getId());
        assertEquals("testuser", result.getData().getUsername());
    }

    @Test
    void testSettersAndGetters() {
        McpResult<String> result = new McpResult<>();
        result.setSuccess(true);
        result.setData("data");
        result.setMessage("message");
        result.setErrorCode("CODE");
        
        assertTrue(result.isSuccess());
        assertEquals("data", result.getData());
        assertEquals("message", result.getMessage());
        assertEquals("CODE", result.getErrorCode());
    }

    static class TestUser {
        private String id;
        private String username;
        private String email;

        public TestUser(String id, String username, String email) {
            this.id = id;
            this.username = username;
            this.email = email;
        }

        public String getId() { return id; }
        public String getUsername() { return username; }
        public String getEmail() { return email; }
    }
}
