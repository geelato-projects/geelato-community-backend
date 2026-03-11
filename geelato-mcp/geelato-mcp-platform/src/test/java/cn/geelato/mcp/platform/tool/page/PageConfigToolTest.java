package cn.geelato.mcp.platform.tool.page;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PageConfigTool 单元测试
 * 测试页面配置工具的方法
 */
@SpringBootTest
@DisplayName("PageConfigTool 单元测试")
public class PageConfigToolTest {

    @Autowired
    private PageConfigTool pageConfigTool;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("测试 listAllPages 方法 - 获取所有页面配置列表")
    void testListAllPages() throws Exception {
        // 调用方法
        String result = pageConfigTool.listAllPages();

        // 验证结果
        assertNotNull(result, "结果不应为空");
        assertTrue(result.contains("user-list"), "应包含 user-list 页面");
        assertTrue(result.contains("order-detail"), "应包含 order-detail 页面");
        assertTrue(result.contains("用户列表页"), "应包含用户列表页标题");
        assertTrue(result.contains("订单详情页"), "应包含订单详情页标题");

        // 验证 JSON 格式
        JsonNode contentNode = objectMapper.readTree(result);
        assertTrue(contentNode.isArray(), "内容应为数组");
        assertTrue(contentNode.size() >= 2, "应至少包含 2 个页面配置");

        // 验证第一个页面的结构
        JsonNode firstPage = contentNode.get(0);
        assertNotNull(firstPage.get("pageId"), "页面应有 pageId 字段");
        assertNotNull(firstPage.get("title"), "页面应有 title 字段");
        assertNotNull(firstPage.get("type"), "页面应有 type 字段");
        assertNotNull(firstPage.get("entity"), "页面应有 entity 字段");
    }

    @Test
    @DisplayName("测试 getPageConfig 方法 - 根据页面ID查询配置")
    void testGetPageConfig() throws Exception {
        // 调用方法
        String pageId = "test-page-001";
        String result = pageConfigTool.getPageConfig(pageId);

        // 验证结果
        assertNotNull(result, "结果不应为空");
        assertTrue(result.contains(pageId), "应包含请求的页面ID");
        assertTrue(result.contains("页面 " + pageId), "应包含页面标题");
        assertTrue(result.contains("components"), "应包含 components 字段");

        // 验证 JSON 结构
        JsonNode contentNode = objectMapper.readTree(result);
        assertEquals(pageId, contentNode.get("pageId").asText(), "pageId 应匹配");
        assertNotNull(contentNode.get("title"), "应有 title 字段");
        assertNotNull(contentNode.get("type"), "应有 type 字段");
        assertNotNull(contentNode.get("description"), "应有 description 字段");
        assertTrue(contentNode.get("components").isArray(), "components 应为数组");
    }

    @Test
    @DisplayName("测试 getPagesByEntity 方法 - 根据实体名称查询页面")
    void testGetPagesByEntity() throws Exception {
        // 调用方法
        String entityName = "platform_user";
        String result = pageConfigTool.getPagesByEntity(entityName);

        // 验证结果
        assertNotNull(result, "结果不应为空");
        assertTrue(result.contains(entityName), "应包含实体名称");
        assertTrue(result.contains(entityName + "-list"), "应包含实体关联的页面ID");
        assertTrue(result.contains("列表页"), "应包含列表页标题");

        // 验证 JSON 结构
        JsonNode contentNode = objectMapper.readTree(result);
        assertTrue(contentNode.isArray(), "内容应为数组");
        assertTrue(contentNode.size() > 0, "应至少返回一个页面");

        JsonNode firstPage = contentNode.get(0);
        assertNotNull(firstPage.get("pageId"), "页面应有 pageId 字段");
        assertNotNull(firstPage.get("title"), "页面应有 title 字段");
        assertEquals(entityName, firstPage.get("entity").asText(), "entity 应匹配");
    }
}
