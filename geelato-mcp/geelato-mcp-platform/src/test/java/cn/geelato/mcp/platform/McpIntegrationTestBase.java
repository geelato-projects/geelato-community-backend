package cn.geelato.mcp.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MCP 集成测试基类
 * 提供标准的 MCP 协议测试方法
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class McpIntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected static final String API_KEY = "test-api-key-123456";
    protected static final String MCP_MESSAGE_ENDPOINT = "/mcp/message";

    @BeforeEach
    void setUp() {
        // 测试前的初始化
    }

    /**
     * 调用 MCP 工具
     *
     * @param toolName 工具名称
     * @param arguments 工具参数
     * @return 工具返回结果
     * @throws Exception 调用异常
     */
    protected String callTool(String toolName, String arguments) throws Exception {
        String requestBody = String.format(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"%s\",\"arguments\":%s}}",
            toolName, arguments
        );

        MvcResult result = mockMvc.perform(post(MCP_MESSAGE_ENDPOINT)
                .header("X-API-Key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andReturn();

        return result.getResponse().getContentAsString();
    }

    /**
     * 获取可用工具列表
     *
     * @return 工具列表 JSON
     * @throws Exception 调用异常
     */
    protected String listTools() throws Exception {
        String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";

        MvcResult result = mockMvc.perform(post(MCP_MESSAGE_ENDPOINT)
                .header("X-API-Key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andReturn();

        return result.getResponse().getContentAsString();
    }

    /**
     * 验证 JSON 响应是否包含预期内容
     *
     * @param jsonResponse JSON 响应
     * @param expectedField 预期字段
     * @param expectedValue 预期值
     * @return 是否匹配
     * @throws Exception 解析异常
     */
    protected boolean assertJsonContains(String jsonResponse, String expectedField, String expectedValue) throws Exception {
        JsonNode root = objectMapper.readTree(jsonResponse);
        JsonNode resultNode = root.path("result").path("content").get(0).path("text");
        String content = resultNode.asText();
        return content.contains(expectedValue);
    }

    /**
     * 从 JSON 响应中提取文本内容
     *
     * @param jsonResponse JSON 响应
     * @return 文本内容
     * @throws Exception 解析异常
     */
    protected String extractTextContent(String jsonResponse) throws Exception {
        JsonNode root = objectMapper.readTree(jsonResponse);
        JsonNode resultNode = root.path("result").path("content").get(0).path("text");
        return resultNode.asText();
    }
}
