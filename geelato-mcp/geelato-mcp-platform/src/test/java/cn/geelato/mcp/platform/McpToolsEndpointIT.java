package cn.geelato.mcp.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@DisplayName("MCP /api/mcp/tools HTTP 链路集成测试")
public class McpToolsEndpointIT extends McpPlatformMysqlContainerSupport {

    private static final String API_KEY = "test-api-key-123456";
    private static ConfigurableApplicationContext context;
    private static int port;

    @BeforeAll
    static void startServer() {
        assumeMysqlStarted();
        String[] args = concat(
                new String[]{"--server.port=0", "--spring.profiles.active=test"},
                mysqlDatasourceArgs()
        );
        context = SpringApplication.run(McpPlatformApplication.class, args);
        port = Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
    }

    @AfterAll
    static void stopServer() {
        if (context != null) {
            context.close();
        }
        stopMysqlIfStarted();
    }

    @Test
    @DisplayName("GET /api/mcp/tools 返回工具清单")
    void shouldListTools() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/mcp/tools"))
                .header("X-API-Key", API_KEY)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        JsonNode root = mapper.readTree(response.body());

        org.junit.jupiter.api.Assertions.assertEquals(200, response.statusCode());
        org.junit.jupiter.api.Assertions.assertEquals(200, root.path("code").asInt());
        org.junit.jupiter.api.Assertions.assertTrue(root.path("data").has("SystemInfoTool"));
    }

    private static String[] concat(String[] left, String[] right) {
        int l = left == null ? 0 : left.length;
        int r = right == null ? 0 : right.length;
        String[] out = new String[l + r];
        if (l > 0) {
            System.arraycopy(left, 0, out, 0, l);
        }
        if (r > 0) {
            System.arraycopy(right, 0, out, l, r);
        }
        return out;
    }
}
