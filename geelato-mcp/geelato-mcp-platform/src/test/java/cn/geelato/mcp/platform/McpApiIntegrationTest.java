package cn.geelato.mcp.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("MCP Platform API 集成测试")
public class McpApiIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected static final String API_KEY = "test-api-key-123456";
    protected static final String API_BASE = "/api/mcp";

    @BeforeEach
    void setUp() {
    }

    @Nested
    @DisplayName("认证测试")
    class AuthenticationTests {

        @Test
        @DisplayName("有效 API Key 应该通过认证")
        void testValidApiKey() throws Exception {
            mockMvc.perform(get(API_BASE + "/tools")
                    .header("X-API-Key", API_KEY))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("无效 API Key 应该被拒绝")
        void testInvalidApiKey() throws Exception {
            mockMvc.perform(get(API_BASE + "/tools")
                    .header("X-API-Key", "invalid-key"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("缺少 API Key 应该被拒绝")
        void testMissingApiKey() throws Exception {
            mockMvc.perform(get(API_BASE + "/tools"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("OPTIONS 请求应该跳过认证")
        void testOptionsRequest() throws Exception {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .options(API_BASE + "/tools"))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("工具列表接口测试")
    class ToolsListTests {

        @Test
        @DisplayName("获取所有工具列表")
        void testListTools() throws Exception {
            MvcResult result = mockMvc.perform(get(API_BASE + "/tools")
                    .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andReturn();

            String response = result.getResponse().getContentAsString();
            JsonNode root = objectMapper.readTree(response);
            JsonNode tools = root.path("data");

            assertTrue(tools.has("SystemInfoTool"), "应包含 SystemInfoTool");
            assertTrue(tools.has("UserQueryTool"), "应包含 UserQueryTool");
            assertTrue(tools.has("DictQueryTool"), "应包含 DictQueryTool");
            assertTrue(tools.has("PageConfigTool"), "应包含 PageConfigTool");
            assertTrue(tools.has("ViewQueryTool"), "应包含 ViewQueryTool");
            assertTrue(tools.has("MetaModelTool"), "应包含 MetaModelTool");
        }

        @Test
        @DisplayName("验证工具列表结构")
        void testToolsListStructure() throws Exception {
            MvcResult result = mockMvc.perform(get(API_BASE + "/tools")
                    .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andReturn();

            String response = result.getResponse().getContentAsString();
            JsonNode root = objectMapper.readTree(response);
            JsonNode systemTools = root.path("data").path("SystemInfoTool");

            assertTrue(systemTools.has("getSystemInfo"), "应包含 getSystemInfo 方法");
            assertTrue(systemTools.has("getMemoryInfo"), "应包含 getMemoryInfo 方法");
            assertTrue(systemTools.has("getCpuInfo"), "应包含 getCpuInfo 方法");
        }
    }

    @Nested
    @DisplayName("工具调用接口测试")
    class ToolCallTests {

        @Test
        @DisplayName("调用 SystemInfoTool - getSystemInfo")
        void testCallGetSystemInfo() throws Exception {
            String requestBody = objectMapper.writeValueAsString(
                new McpToolCallRequest("SystemInfoTool", "getSystemInfo", null)
            );

            MvcResult result = mockMvc.perform(post(API_BASE + "/tool/call")
                    .header("X-API-Key", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

            String response = result.getResponse().getContentAsString();
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");

            assertTrue(data.has("javaVersion") || data.has("osName"), 
                "系统信息应包含 Java 版本或操作系统信息");
        }

        @Test
        @DisplayName("调用 SystemInfoTool - getMemoryInfo")
        void testCallGetMemoryInfo() throws Exception {
            String requestBody = objectMapper.writeValueAsString(
                new McpToolCallRequest("SystemInfoTool", "getMemoryInfo", null)
            );

            mockMvc.perform(post(API_BASE + "/tool/call")
                    .header("X-API-Key", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("调用 SystemInfoTool - getCpuInfo")
        void testCallGetCpuInfo() throws Exception {
            String requestBody = objectMapper.writeValueAsString(
                new McpToolCallRequest("SystemInfoTool", "getCpuInfo", null)
            );

            mockMvc.perform(post(API_BASE + "/tool/call")
                    .header("X-API-Key", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("调用 UserQueryTool - listAllUsers")
        void testCallListAllUsers() throws Exception {
            String requestBody = objectMapper.writeValueAsString(
                new McpToolCallRequest("UserQueryTool", "listAllUsers", null)
            );

            mockMvc.perform(post(API_BASE + "/tool/call")
                    .header("X-API-Key", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("调用 DictQueryTool - listAllDictTypes")
        void testCallListAllDictTypes() throws Exception {
            String requestBody = objectMapper.writeValueAsString(
                new McpToolCallRequest("DictQueryTool", "listAllDictTypes", null)
            );

            mockMvc.perform(post(API_BASE + "/tool/call")
                    .header("X-API-Key", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("调用 PageConfigTool - listAllPages")
        void testCallListAllPages() throws Exception {
            String requestBody = objectMapper.writeValueAsString(
                new McpToolCallRequest("PageConfigTool", "listAllPages", null)
            );

            mockMvc.perform(post(API_BASE + "/tool/call")
                    .header("X-API-Key", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("调用 MetaModelTool - listAllEntityNames")
        void testCallListAllEntityNames() throws Exception {
            String requestBody = objectMapper.writeValueAsString(
                new McpToolCallRequest("MetaModelTool", "listAllEntityNames", null)
            );

            mockMvc.perform(post(API_BASE + "/tool/call")
                    .header("X-API-Key", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("调用不存在的工具应该返回错误")
        void testCallUnknownTool() throws Exception {
            String requestBody = objectMapper.writeValueAsString(
                new McpToolCallRequest("UnknownTool", "unknownMethod", null)
            );

            mockMvc.perform(post(API_BASE + "/tool/call")
                    .header("X-API-Key", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().is5xxServerError());
        }

        @Test
        @DisplayName("调用不存在的方法应该返回错误")
        void testCallUnknownMethod() throws Exception {
            String requestBody = objectMapper.writeValueAsString(
                new McpToolCallRequest("SystemInfoTool", "unknownMethod", null)
            );

            mockMvc.perform(post(API_BASE + "/tool/call")
                    .header("X-API-Key", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().is5xxServerError());
        }
    }

    @Nested
    @DisplayName("带参数的工具调用测试")
    class ToolCallWithParamsTests {

        @Test
        @DisplayName("调用 UserQueryTool - getUserById")
        void testCallGetUserById() throws Exception {
            java.util.Map<String, Object> params = new java.util.HashMap<>();
            params.put("userId", "6652990717893414912");

            String requestBody = objectMapper.writeValueAsString(
                new McpToolCallRequest("UserQueryTool", "getUserById", params)
            );

            mockMvc.perform(post(API_BASE + "/tool/call")
                    .header("X-API-Key", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("调用 DictQueryTool - getDictItems")
        void testCallGetDictItems() throws Exception {
            java.util.Map<String, Object> params = new java.util.HashMap<>();
            params.put("dictCode", "multiLangType");

            String requestBody = objectMapper.writeValueAsString(
                new McpToolCallRequest("DictQueryTool", "getDictItems", params)
            );

            mockMvc.perform(post(API_BASE + "/tool/call")
                    .header("X-API-Key", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("调用 PageConfigTool - getPageConfig")
        void testCallGetPageConfig() throws Exception {
            java.util.Map<String, Object> params = new java.util.HashMap<>();
            params.put("pageId", "user-list");

            String requestBody = objectMapper.writeValueAsString(
                new McpToolCallRequest("PageConfigTool", "getPageConfig", params)
            );

            mockMvc.perform(post(API_BASE + "/tool/call")
                    .header("X-API-Key", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("调用 MetaModelTool - getEntityMeta")
        void testCallGetEntityMeta() throws Exception {
            java.util.Map<String, Object> params = new java.util.HashMap<>();
            params.put("entityName", "platform_user");

            String requestBody = objectMapper.writeValueAsString(
                new McpToolCallRequest("MetaModelTool", "getEntityMeta", params)
            );

            mockMvc.perform(post(API_BASE + "/tool/call")
                    .header("X-API-Key", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        }
    }

    static class McpToolCallRequest {
        private String tool;
        private String method;
        private java.util.Map<String, Object> params;

        public McpToolCallRequest(String tool, String method, java.util.Map<String, Object> params) {
            this.tool = tool;
            this.method = method;
            this.params = params;
        }

        public String getTool() { return tool; }
        public void setTool(String tool) { this.tool = tool; }
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        public java.util.Map<String, Object> getParams() { return params; }
        public void setParams(java.util.Map<String, Object> params) { this.params = params; }
    }
}
