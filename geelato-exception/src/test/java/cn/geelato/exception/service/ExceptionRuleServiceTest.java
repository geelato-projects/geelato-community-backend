package cn.geelato.exception.service;

import cn.geelato.exception.entity.FmsExceptionRule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ExceptionRuleService 单元测试（契约面：可离线完成的纯逻辑）。
 *
 * 覆盖场景（不触碰数据库与会话上下文）：
 * - documentGate：原文档级门禁（exceptionVersion 缺省 geelato/v1、kind 白名单）
 * - contractCheck：由规范化列重建文档的结构 + 必填矩阵（metadata / 步骤 / 升级链）
 * - trialRun：dry-run 占位结构完整（executed=false、候选 0、每步 SKIPPED、复用 contractCheck 结论）
 * - exportYaml / buildDocument：exceptionVersion 恒回填、引擎版本不进文档
 */
class ExceptionRuleServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ExceptionRuleService service = new ExceptionRuleService();

    /** 由规范化列构造实体（buildDocument 输入形态，等价库内行）。 */
    private FmsExceptionRule ruleOf(String pipelineJson) {
        FmsExceptionRule rule = new FmsExceptionRule();
        rule.setRuleCode("EXC-001");
        rule.setRuleName("示例规则");
        rule.setRuleKind("ALERT");
        rule.setTriggerType("SCAN");
        rule.setPipelineJson(pipelineJson);
        return rule;
    }

    private String validStepsJson() {
        return "{\"steps\":[{\"name\":\"初筛\",\"lang\":\"js\",\"content\":\"return true;\"}]}";
    }

    private String text(Map<String, Object> issue, String key) {
        Object v = issue.get(key);
        return v == null ? null : String.valueOf(v);
    }

    // ==================== 应用归属（appId）====================

    @Test
    @DisplayName("pageQuery：appId 为空 → fail-closed 空集（不触库）")
    void pageQueryFailClosedWithoutAppId() {
        ExceptionRuleService.RuleQueryParams params = new ExceptionRuleService.RuleQueryParams();
        Map<String, Object> result = service.pageQuery(params);
        assertTrue(((List<?>) result.get("list")).isEmpty());
        assertEquals(0L, result.get("total"));
        assertEquals(false, result.get("hasMore"));
    }

    @Test
    @DisplayName("save：appId 为空 → IllegalArgumentException（严格归属校验，未触库）")
    void saveRejectsBlankAppId() throws Exception {
        ExceptionRuleService.RuleSaveRequest req = new ExceptionRuleService.RuleSaveRequest();
        req.setDocument(MAPPER.readTree(validAlertRule()));
        // 不设置 appId
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(req));
        assertTrue(ex.getMessage().contains("appId"), "应提示 appId 不能为空，实际：" + ex.getMessage());
    }

    // ==================== documentGate（原文档级门禁）====================

    @Test
    @DisplayName("documentGate：exceptionVersion 缺失或 geelato/v1 均放行（默认值）")
    void documentGateExceptionVersionDefault() throws Exception {
        assertTrue(service.documentGate(MAPPER.readTree(validAlertRule())).isEmpty(),
                "显式 exceptionVersion=geelato/v1 应放行");
        JsonNode noVersion = MAPPER.readTree(validAlertRule());
        ((com.fasterxml.jackson.databind.node.ObjectNode) noVersion).remove("exceptionVersion");
        assertTrue(service.documentGate(noVersion).isEmpty(), "缺失 exceptionVersion 应按默认放行");
    }

    @Test
    @DisplayName("documentGate：显式非法 exceptionVersion → 定位拒绝；纯旧 apiVersion 字段（缺 exceptionVersion）→ 按默认放行")
    void documentGateExceptionVersionRules() throws Exception {
        JsonNode invalid = MAPPER.readTree(validAlertRule()
                .replace("\"exceptionVersion\": \"geelato/v1\"", "\"exceptionVersion\": \"geelato/v9\""));
        assertTrue(service.documentGate(invalid).stream().anyMatch(i -> "EXCEPTION_VERSION_INVALID".equals(text(i, "code"))
                        && "exceptionVersion".equals(text(i, "fieldPath"))),
                "显式非法值应定位到 exceptionVersion");

        JsonNode legacy = MAPPER.readTree(validAlertRule()
                .replace("\"exceptionVersion\": \"geelato/v1\"", "\"apiVersion\": \"exception.geelato/v2\""));
        assertTrue(service.documentGate(legacy).isEmpty(),
                "旧版文档缺 exceptionVersion 应按默认放行（apiVersion 为未知字段被忽略）");
    }

    @Test
    @DisplayName("save：exceptionVersion 非法 → 门禁拒绝（先于触库，消息含 issues JSON）")
    void saveRejectsInvalidExceptionVersion() throws Exception {
        ExceptionRuleService.RuleSaveRequest req = new ExceptionRuleService.RuleSaveRequest();
        req.setDocument(MAPPER.readTree(validAlertRule()
                .replace("\"exceptionVersion\": \"geelato/v1\"", "\"exceptionVersion\": \"exception.geelato/v2\"")));
        req.setAppId("app-1");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(req));
        assertTrue(ex.getMessage().contains("EXCEPTION_VERSION_INVALID")
                && ex.getMessage().contains("exceptionVersion"), "应含字段定位，实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("documentGate：kind 缺失/非法 → KIND_INVALID（不再静默按 ALERT 落库）")
    void documentGateKindWhitelist() throws Exception {
        JsonNode noKind = MAPPER.readTree(validAlertRule());
        ((com.fasterxml.jackson.databind.node.ObjectNode) noKind).remove("kind");
        assertTrue(service.documentGate(noKind).stream()
                .anyMatch(i -> "KIND_INVALID".equals(text(i, "code"))));
        JsonNode weirdKind = MAPPER.readTree(validAlertRule()
                .replace("\"AlertRule\"", "\"WeirdKind\""));
        assertTrue(service.documentGate(weirdKind).stream()
                .anyMatch(i -> "KIND_INVALID".equals(text(i, "code")) && "kind".equals(text(i, "fieldPath"))));
    }

    // ==================== 引擎版本零入口 ====================

    @Test
    @DisplayName("applyDocument：文档无论写什么都不得污染 engine_version（含旧 apiVersion 字段，保存即清空）")
    void applyDocumentClearsEngineVersion() throws Exception {
        FmsExceptionRule rule = ruleOf(validStepsJson());
        rule.setEngineVersion("v2");
        Method apply = ExceptionRuleService.class.getDeclaredMethod("applyDocument",
                FmsExceptionRule.class, JsonNode.class);
        apply.setAccessible(true);
        apply.invoke(service, rule, MAPPER.readTree(validAlertRule()
                .replace("\"exceptionVersion\": \"geelato/v1\"", "\"apiVersion\": \"exception.geelato/v2\"")));
        assertNull(rule.getEngineVersion(), "engine_version 应恒空（=引擎缺省），实际: " + rule.getEngineVersion());
    }

    @Test
    @DisplayName("exportYaml：存量脏 engine_version 不进文档，exceptionVersion 恒回填且无 apiVersion 字段")
    void exportYamlDropsEngineVersionAndFillsExceptionVersion() {
        FmsExceptionRule rule = ruleOf(validStepsJson());
        rule.setEngineVersion("v2");
        String yaml = service.exportYaml(rule);
        assertTrue(yaml.contains("exceptionVersion: geelato/v1"), "应回填常量文档版本，实际：" + yaml);
        assertFalse(yaml.contains("apiVersion"), "不应再输出 apiVersion 字段，实际：" + yaml);
        assertFalse(yaml.contains("engineVersion") || yaml.contains("v2"), "引擎版本不应进文档，实际：" + yaml);
    }

    // ==================== contractCheck（重建文档结构校验）====================

    @Test
    @DisplayName("contractCheck：合法 AlertRule 通过（无 issue）")
    void contractCheckPass() {
        List<Map<String, Object>> issues = service.contractCheck(ruleOf(validStepsJson()));
        assertTrue(issues.isEmpty(), "合法文档应零问题，实际：" + issues);
    }

    @Test
    @DisplayName("contractCheck：pipeline_json 列为坏 JSON / 空 → steps REQUIRED（脏列不放行）")
    void contractCheckDirtyPipelineColumn() {
        assertTrue(service.contractCheck(ruleOf("{ not json")).stream()
                .anyMatch(i -> "spec.pipeline.steps".equals(text(i, "fieldPath"))));
        assertTrue(service.contractCheck(ruleOf(null)).stream()
                .anyMatch(i -> "spec.pipeline.steps".equals(text(i, "fieldPath"))));
    }

    @Test
    @DisplayName("contractCheck：管道步骤语言非法 + 脚本为空 → 两条步骤 issue")
    void contractCheckStepValidation() {
        String pipelineJson = "{\"steps\":[{\"name\":\"坏步骤\",\"lang\":\"ruby\",\"content\":\"\"}]}";
        List<Map<String, Object>> issues = service.contractCheck(ruleOf(pipelineJson));
        assertTrue(issues.stream().anyMatch(i -> "LANG_INVALID".equals(text(i, "code"))
                && text(i, "fieldPath").contains("steps[0].lang")));
        assertTrue(issues.stream().anyMatch(i -> "REQUIRED".equals(text(i, "code"))
                && text(i, "fieldPath").endsWith("steps[0].content")));
    }

    @Test
    @DisplayName("contractCheck：管道为空 → steps REQUIRED")
    void contractCheckPipelineRequired() {
        List<Map<String, Object>> issues = service.contractCheck(ruleOf("{\"steps\":[]}"));
        assertTrue(issues.stream().anyMatch(i -> "spec.pipeline.steps".equals(text(i, "fieldPath"))));
    }

    @Test
    @DisplayName("contractCheck：EscalateRule 缺 escalationOf → 命中升级链必填")
    void contractCheckEscalationRequired() {
        FmsExceptionRule rule = ruleOf("{\"steps\":[{\"name\":\"s\",\"lang\":\"python\",\"content\":\"x=1\"}]}");
        rule.setRuleKind("ESCALATE");
        List<Map<String, Object>> issues = service.contractCheck(rule);
        assertTrue(issues.stream().anyMatch(i -> "spec.escalationOf".equals(text(i, "fieldPath"))
                && "REQUIRED".equals(text(i, "code"))));
    }

    // ==================== trialRun ====================

    @Test
    @DisplayName("trialRun：dry-run 占位结构完整，真实执行标记 executed=false")
    void trialRunPlaceholderShape() {
        Map<String, Object> result = service.trialRun(ruleOf(validStepsJson()), "SCAN");
        assertEquals(0, result.get("candidateCount"));
        assertEquals(0, result.get("hitCount"));
        assertEquals(false, result.get("executed"));
        assertEquals("SCAN", result.get("target"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) result.get("steps");
        assertEquals(1, steps.size());
        assertEquals("SKIPPED", steps.get(0).get("status"));

        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) result.get("contractCheck");
        assertEquals("PASS", contract.get("status"));
    }

    @Test
    @DisplayName("trialRun：契约门禁失败时 status=FAIL 并回传 errors")
    void trialRunSurfacesContractFailure() {
        Map<String, Object> result = service.trialRun(ruleOf(null), "SCAN");
        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) result.get("contractCheck");
        assertEquals("FAIL", contract.get("status"));
        assertFalse(((List<?>) contract.get("errors")).isEmpty());
    }

    // ==================== exportYaml ====================

    @Test
    @DisplayName("exportYaml：由规范化列导出含 metadata.code 的 YAML（可 round-trip 解析）")
    void exportYamlContainsCode() {
        String yaml = service.exportYaml(ruleOf(validStepsJson()));
        assertTrue(yaml.contains("EXC-001"), "YAML 应含规则编码，实际：" + yaml);
        JsonNode reparsed = new org.yaml.snakeyaml.Yaml()
                .load(yaml) instanceof Map<?, ?> m ? MAPPER.valueToTree(m) : null;
        assertEquals("EXC-001", reparsed.path("metadata").path("code").asText());
        assertEquals("geelato/v1", reparsed.path("exceptionVersion").asText());
    }

    // ==================== importYamlBatch（离线可测面：解析与逐条隔离）====================

    @Test
    @DisplayName("importYamlBatch：空/纯注释内容拒绝")
    void importYamlBatchRejectsBlank() {
        IllegalArgumentException empty = assertThrows(IllegalArgumentException.class,
                () -> service.importYamlBatch("", "app-1"));
        assertTrue(empty.getMessage().contains("为空"));
        IllegalArgumentException comments = assertThrows(IllegalArgumentException.class,
                () -> service.importYamlBatch("# 只有注释\n", "app-1"));
        assertTrue(comments.getMessage().contains("为空"));
    }

    @Test
    @DisplayName("importYamlBatch：多文档逐条隔离——单条失败不中断，结果逐条返回失败原因")
    void importYamlBatchIsolatesPerDocumentFailure() {
        String yaml = String.join("\n---\n", List.of(
                "kind: AlertRule\nmetadata: { code: BAD-1 }\nspec: {}",
                "kind: AlertRule\nmetadata: { code: BAD-2, name: 缺管道 }\nspec:\n  trigger: { type: SCAN }\n  pipeline: {}"));
        List<Map<String, Object>> results = service.importYamlBatch(yaml, "app-1");
        assertEquals(2, results.size());
        assertEquals("BAD-1", results.get(0).get("ruleCode"));
        assertEquals(Boolean.FALSE, results.get(0).get("success"));
        assertTrue(String.valueOf(results.get(0).get("error")).contains("门禁未通过"));
        assertEquals("BAD-2", results.get(1).get("ruleCode"));
        assertEquals(Boolean.FALSE, results.get(1).get("success"));
    }

    /** 校验通过的最小 AlertRule 文档（exceptionVersion 显式合法值）。 */
    private String validAlertRule() {
        return """
                {
                  "exceptionVersion": "geelato/v1",
                  "kind": "AlertRule",
                  "metadata": { "code": "EXC-001", "name": "示例规则" },
                  "spec": {
                    "trigger": { "type": "SCAN" },
                    "pipeline": { "steps": [
                      { "name": "初筛", "lang": "js", "content": "return true;" }
                    ] }
                  }
                }
                """;
    }
}
