package cn.geelato.mcp.platform;

import cn.geelato.mcp.platform.tool.page.PageConfigTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP 工具验证测试
 * 验证通过服务调用各工具是否正常工作
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("MCP 工具验证测试")
public class McpToolVerificationTest {

    @Autowired
    private PageConfigTool pageConfigTool;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // 测试前的初始化
    }

    @Test
    @DisplayName("验证 listAllPages 工具返回正确的页面列表")
    void testListAllPagesTool() throws Exception {
        // 调用工具
        String result = pageConfigTool.listAllPages();

        // 验证结果不为空
        assertNotNull(result, "工具返回结果不应为空");
        assertFalse(result.isEmpty(), "工具返回结果不应为空字符串");

        // 验证 JSON 格式正确
        JsonNode pages = objectMapper.readTree(result);
        assertTrue(pages.isArray(), "返回结果应为数组");
        assertTrue(pages.size() >= 2, "应至少包含 2 个页面");

        // 验证第一个页面的结构
        JsonNode firstPage = pages.get(0);
        assertTrue(firstPage.has("pageId"), "页面应有 pageId 字段");
        assertTrue(firstPage.has("title"), "页面应有 title 字段");
        assertTrue(firstPage.has("type"), "页面应有 type 字段");
        assertTrue(firstPage.has("entity"), "页面应有 entity 字段");

        // 验证页面内容
        String pageId = firstPage.get("pageId").asText();
        String title = firstPage.get("title").asText();
        assertNotNull(pageId, "pageId 不应为空");
        assertNotNull(title, "title 不应为空");

        System.out.println("✅ listAllPages 工具测试通过");
        System.out.println("   返回页面数: " + pages.size());
        System.out.println("   第一个页面: " + pageId + " - " + title);
    }

    @Test
    @DisplayName("验证 getPageConfig 工具返回正确的页面配置")
    void testGetPageConfigTool() throws Exception {
        // 调用工具
        String pageId = "test-page-001";
        String result = pageConfigTool.getPageConfig(pageId);

        // 验证结果不为空
        assertNotNull(result, "工具返回结果不应为空");
        assertFalse(result.isEmpty(), "工具返回结果不应为空字符串");

        // 验证 JSON 格式正确
        JsonNode config = objectMapper.readTree(result);
        assertTrue(config.isObject(), "返回结果应为对象");

        // 验证配置结构
        assertTrue(config.has("pageId"), "配置应有 pageId 字段");
        assertTrue(config.has("title"), "配置应有 title 字段");
        assertTrue(config.has("type"), "配置应有 type 字段");
        assertTrue(config.has("description"), "配置应有 description 字段");
        assertTrue(config.has("components"), "配置应有 components 字段");

        // 验证值
        assertEquals(pageId, config.get("pageId").asText(), "pageId 应匹配");
        assertTrue(config.get("title").asText().contains(pageId), "title 应包含 pageId");
        assertTrue(config.get("components").isArray(), "components 应为数组");

        System.out.println("✅ getPageConfig 工具测试通过");
        System.out.println("   页面ID: " + pageId);
        System.out.println("   标题: " + config.get("title").asText());
        System.out.println("   组件数: " + config.get("components").size());
    }

    @Test
    @DisplayName("验证 getPagesByEntity 工具返回正确的实体页面")
    void testGetPagesByEntityTool() throws Exception {
        // 调用工具
        String entityName = "platform_user";
        String result = pageConfigTool.getPagesByEntity(entityName);

        // 验证结果不为空
        assertNotNull(result, "工具返回结果不应为空");
        assertFalse(result.isEmpty(), "工具返回结果不应为空字符串");

        // 验证 JSON 格式正确
        JsonNode pages = objectMapper.readTree(result);
        assertTrue(pages.isArray(), "返回结果应为数组");
        assertTrue(pages.size() > 0, "应至少返回一个页面");

        // 验证第一个页面的结构
        JsonNode firstPage = pages.get(0);
        assertTrue(firstPage.has("pageId"), "页面应有 pageId 字段");
        assertTrue(firstPage.has("title"), "页面应有 title 字段");
        assertTrue(firstPage.has("entity"), "页面应有 entity 字段");

        // 验证实体匹配
        assertEquals(entityName, firstPage.get("entity").asText(), "entity 应匹配");
        assertTrue(firstPage.get("pageId").asText().contains(entityName), "pageId 应包含实体名称");

        System.out.println("✅ getPagesByEntity 工具测试通过");
        System.out.println("   实体名称: " + entityName);
        System.out.println("   返回页面数: " + pages.size());
        System.out.println("   页面ID: " + firstPage.get("pageId").asText());
    }

    @Test
    @DisplayName("验证所有工具返回的数据格式一致性")
    void testToolDataFormatConsistency() throws Exception {
        // 测试 listAllPages
        String listResult = pageConfigTool.listAllPages();
        JsonNode listPages = objectMapper.readTree(listResult);
        assertTrue(listPages.isArray(), "listAllPages 应返回数组");

        // 测试 getPageConfig
        String configResult = pageConfigTool.getPageConfig("test-page");
        JsonNode config = objectMapper.readTree(configResult);
        assertTrue(config.isObject(), "getPageConfig 应返回对象");

        // 测试 getPagesByEntity
        String entityResult = pageConfigTool.getPagesByEntity("test_entity");
        JsonNode entityPages = objectMapper.readTree(entityResult);
        assertTrue(entityPages.isArray(), "getPagesByEntity 应返回数组");

        System.out.println("✅ 所有工具数据格式一致性测试通过");
        System.out.println("   listAllPages: 返回 " + listPages.size() + " 个页面");
        System.out.println("   getPageConfig: 返回有效配置对象");
        System.out.println("   getPagesByEntity: 返回 " + entityPages.size() + " 个页面");
    }
}
