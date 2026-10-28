package cn.geelato.alert.service;

import cn.geelato.alert.entity.FmsAlertRule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AlertRuleService 单元测试（可离线完成的纯逻辑：match 事件源自描述契约）。
 *
 * 覆盖场景（不触碰数据库与会话上下文）：
 * - applyDocument：match 缺 source 落库前规范化补默认 EXCEPTION，显式 source 原样保留；
 * - contractCheck：合法 EXCEPTION 源通过、未知/缺失 source 硬失败（SOURCE_INVALID）；
 * - detail：输出 source 与 referencedRuleCodes 角标（仅 EXCEPTION 源）。
 */
class AlertRuleServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AlertRuleService service = new AlertRuleService();

    private FmsAlertRule ruleFrom(String docJson) throws Exception {
        FmsAlertRule rule = new FmsAlertRule();
        service.applyDocument(rule, MAPPER.readTree(docJson));
        return rule;
    }

    private static final String VALID_DOC = """
            {"metadata":{"code":"ALR-001","name":"测试规则"},
             "spec":{"match":{"events":["EXC_CREATED","EXC_ESCALATED"],"ruleCodes":["EXC-BL-005"]},
                     "trigger":{"mode":"EVENT"},
                     "aggregate":{"mode":"INDEPENDENT"},
                     "receivers":{"extra":[{"type":"USER","userIds":["u1"]}]},
                     "route":{"default":{"channels":["inapp"],"template":"AT-001"}}}}
            """;

    private boolean hasIssue(List<Map<String, Object>> issues, String fieldPath, String code) {
        return issues.stream().anyMatch(i -> fieldPath.equals(i.get("fieldPath")) && code.equals(i.get("code")));
    }

    @Test
    @DisplayName("applyDocument：match 缺 source → 落库前补默认 EXCEPTION")
    void applyDocumentDefaultsSource() throws Exception {
        FmsAlertRule rule = ruleFrom(VALID_DOC);
        JsonNode match = MAPPER.readTree(rule.getMatchJson());
        assertEquals("EXCEPTION", match.path("source").asText());
        assertTrue(match.path("events").isArray(), "原有 events 应保留");
    }

    @Test
    @DisplayName("applyDocument：显式 source 原样保留")
    void applyDocumentKeepsExplicitSource() throws Exception {
        FmsAlertRule rule = ruleFrom(VALID_DOC.replace(
                "\"events\":", "\"source\":\"EXCEPTION\",\"events\":"));
        JsonNode match = MAPPER.readTree(rule.getMatchJson());
        assertEquals("EXCEPTION", match.path("source").asText());
    }

    @Test
    @DisplayName("contractCheck：合法 EXCEPTION 源规则 → 无问题")
    void contractCheckPasses() throws Exception {
        List<Map<String, Object>> issues = service.contractCheck(ruleFrom(VALID_DOC));
        assertTrue(issues.isEmpty(), "应通过，实际：" + issues);
    }

    @Test
    @DisplayName("contractCheck：SCHEDULE 模式 events 可为空（数据集口径不走事件匹配）")
    void contractCheckScheduleExemptsEvents() throws Exception {
        FmsAlertRule rule = ruleFrom("""
                {"metadata":{"code":"ALR-003","name":"每日在途日报"},
                 "spec":{"match":{"events":[]},
                         "trigger":{"mode":"SCHEDULE","cron":"0 0 9 * * ?"},
                         "aggregate":{"mode":"SINGLE"},
                         "receivers":{"extra":[{"type":"ROLE","roleCode":"FIN"}]},
                         "route":{"default":{"channels":["email"],"template":"AT-003"}}}}
                """);
        List<Map<String, Object>> issues = service.contractCheck(rule);
        assertTrue(issues.isEmpty(), "SCHEDULE 应豁免 events 必填，实际：" + issues);
    }

    @Test
    @DisplayName("contractCheck：接收人选择器为空 → REQUIRED 硬失败")
    void contractCheckRejectsEmptyReceivers() throws Exception {
        FmsAlertRule rule = ruleFrom(VALID_DOC.replace(
                "\"receivers\":{\"extra\":[{\"type\":\"USER\",\"userIds\":[\"u1\"]}]}",
                "\"receivers\":{\"extra\":[]}"));
        List<Map<String, Object>> issues = service.contractCheck(rule);
        assertTrue(hasIssue(issues, "spec.receivers.extra", "REQUIRED"), "实际：" + issues);
    }

    @Test
    @DisplayName("contractCheck：接收人选择器类型非法 → SELECTOR_TYPE_INVALID 硬失败")
    void contractCheckRejectsUnknownSelectorType() throws Exception {
        FmsAlertRule rule = ruleFrom(VALID_DOC.replace(
                "{\"type\":\"USER\",\"userIds\":[\"u1\"]}", "{\"type\":\"NOPE\"}"));
        List<Map<String, Object>> issues = service.contractCheck(rule);
        assertTrue(hasIssue(issues, "spec.receivers.extra", "SELECTOR_TYPE_INVALID"), "实际：" + issues);
    }

    @Test
    @DisplayName("contractCheck：未知 source → SOURCE_INVALID 硬失败")
    void contractCheckRejectsUnknownSource() throws Exception {
        FmsAlertRule rule = ruleFrom(VALID_DOC.replace(
                "\"events\":", "\"source\":\"ORDER\",\"events\":"));
        List<Map<String, Object>> issues = service.contractCheck(rule);
        assertTrue(hasIssue(issues, "spec.match.source", "SOURCE_INVALID"), "实际：" + issues);
    }

    @Test
    @DisplayName("contractCheck：手工行未规范化（match 缺 source）→ SOURCE_INVALID 硬失败")
    void contractCheckRejectsMissingSource() throws Exception {
        FmsAlertRule rule = ruleFrom(VALID_DOC);
        rule.setMatchJson("{\"events\":[\"EXC_CREATED\"]}");
        List<Map<String, Object>> issues = service.contractCheck(rule);
        assertTrue(hasIssue(issues, "spec.match.source", "SOURCE_INVALID"), "实际：" + issues);
    }

    @Test
    @DisplayName("detail：EXCEPTION 源输出 source 与 referencedRuleCodes 角标")
    void detailOutputsSourceAndRefs() throws Exception {
        Map<String, Object> detail = service.detail(ruleFrom(VALID_DOC));
        assertEquals("EXCEPTION", detail.get("source"));
        assertEquals(List.of("EXC-BL-005"), detail.get("referencedRuleCodes"));
    }

    @Test
    @DisplayName("detail：非 EXCEPTION 源 referencedRuleCodes 为空")
    void detailSkipsRefsForOtherSource() throws Exception {
        Map<String, Object> detail = service.detail(ruleFrom(VALID_DOC.replace(
                "\"events\":", "\"source\":\"ORDER\",\"events\":")));
        assertEquals("ORDER", detail.get("source"));
        assertEquals(List.of(), detail.get("referencedRuleCodes"));
    }

    @Test
    @DisplayName("save：alertVersion 显式错误值 → 门禁拒绝（缺省同值不校验，未触库）")
    void saveRejectsWrongAlertVersion() throws Exception {
        AlertRuleService.RuleSaveRequest req = new AlertRuleService.RuleSaveRequest();
        req.setAppId("app-1");
        req.setDocument(MAPPER.readTree("""
                {"alertVersion":"geelato/v9","kind":"AlertRule",
                 "metadata":{"code":"ALR-001","name":"测试规则"},
                 "spec":{"match":{"source":"EXCEPTION","events":["EXC_CREATED"]},
                         "trigger":{"mode":"EVENT"},
                         "route":{"default":{"channels":["inapp"],"template":"AT-001"}}}}
                """));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(req));
        assertTrue(ex.getMessage().contains("alertVersion"), "应提示 alertVersion 非法，实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("save：旧文档携带 apiVersion 字段不拦截（未知字段透传，alertVersion 缺省同值）")
    void saveIgnoresLegacyApiVersionField() throws Exception {
        // apiVersion 非门禁字段；不设 appId 触发参数校验先行，证明版本门禁未拦截
        AlertRuleService.RuleSaveRequest req = new AlertRuleService.RuleSaveRequest();
        req.setDocument(MAPPER.readTree(
                "{\"apiVersion\":\"alert.geelato/v1\",\"metadata\":{\"code\":\"ALR-001\"}}"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(req));
        assertTrue(ex.getMessage().contains("appId"), "应落在 appId 校验，实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("detail：重建文档携带 alertVersion=geelato/v1")
    void detailDocumentCarriesAlertVersion() throws Exception {
        Map<String, Object> detail = service.detail(ruleFrom(VALID_DOC));
        JsonNode document = (JsonNode) detail.get("document");
        assertEquals("geelato/v1", document.path("alertVersion").asText());
    }
}
