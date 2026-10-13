package cn.geelato.alert.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AlertTemplateService 单元测试（可离线完成的纯逻辑：上下文字典）。
 *
 * 覆盖场景（不触碰数据库与会话上下文）：
 * - contextSchema：SINGLE/GROUP/SCHEDULE 三种上下文形态的字段树口径与 helper 清单；
 * - type 大小写/空值容错（默认落到 single 分支）。
 */
class AlertTemplateServiceTest {

    private final AlertTemplateService service = new AlertTemplateService();

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fields(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("fields");
    }

    private boolean hasPath(Map<String, Object> result, String path) {
        return fields(result).stream().anyMatch(f -> path.equals(f.get("path")));
    }

    @Test
    @DisplayName("pageQuery：appId 为空 → fail-closed 空集（不触库）")
    void pageQueryFailClosedWithoutAppId() {
        AlertTemplateService.TemplateQueryParams params = new AlertTemplateService.TemplateQueryParams();
        Map<String, Object> result = service.pageQuery(params);
        assertTrue(((List<?>) result.get("list")).isEmpty());
        assertEquals(0L, result.get("total"));
        assertEquals(false, result.get("hasMore"));
    }

    @Test
    @DisplayName("save：appId 为空 → IllegalArgumentException（严格归属校验，未触库）")
    void saveRejectsBlankAppId() throws Exception {
        AlertTemplateService.TemplateSaveRequest req = new AlertTemplateService.TemplateSaveRequest();
        req.setDocument(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree("{\"metadata\":{\"code\":\"TPL-1\"},\"spec\":{\"variants\":[]}}"));
        // 不设置 appId
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(req));
        assertTrue(ex.getMessage().contains("appId"), "应提示 appId 不能为空，实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("contextSchema：single 形态含 exception 字段树与 eventType")
    void contextSchemaSingle() {
        Map<String, Object> result = service.contextSchema("single");
        assertEquals("single", result.get("type"));
        assertTrue(hasPath(result, "exception"));
        assertTrue(hasPath(result, "exception.ruleCode"));
        assertTrue(hasPath(result, "exception.level"));
        assertTrue(hasPath(result, "eventType"));
    }

    @Test
    @DisplayName("contextSchema：group 形态含聚合字段（group.items / stats）")
    void contextSchemaGroup() {
        Map<String, Object> result = service.contextSchema("GROUP");
        assertEquals("group", result.get("type"), "type 应归一为小写");
        assertTrue(hasPath(result, "group"));
        assertTrue(hasPath(result, "group.stats"));
        assertTrue(hasPath(result, "group.items"));
    }

    @Test
    @DisplayName("contextSchema：schedule 形态含 digest 数据集字段")
    void contextSchemaSchedule() {
        Map<String, Object> result = service.contextSchema("schedule");
        assertEquals("schedule", result.get("type"));
        assertTrue(hasPath(result, "digest"));
        assertTrue(hasPath(result, "digest.conditionMet"));
    }

    @Test
    @DisplayName("contextSchema：type 为空默认落到 single 分支")
    void contextSchemaNullDefaultsToSingle() {
        Map<String, Object> result = service.contextSchema(null);
        assertEquals("single", result.get("type"));
        assertTrue(hasPath(result, "exception"));
    }

    @Test
    @DisplayName("contextSchema：三种形态均返回非空 helper 清单与字段树")
    void contextSchemaHelpersPresent() {
        for (String type : List.of("single", "group", "schedule")) {
            Map<String, Object> result = service.contextSchema(type);
            assertFalse(fields(result).isEmpty(), type + " 字段树不应为空");
            @SuppressWarnings("unchecked")
            List<Object> helpers = (List<Object>) result.get("helpers");
            assertFalse(helpers.isEmpty(), type + " helper 清单不应为空");
        }
    }
}
