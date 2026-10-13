package cn.geelato.exception.engine;

import cn.geelato.exception.engine.core.PipelineEngine;
import cn.geelato.exception.engine.core.PipelineOutcome;
import cn.geelato.exception.engine.core.TriggerMode;
import cn.geelato.exception.entity.FmsExceptionRule;
import cn.geelato.script.core.DefaultScriptEngine;
import cn.geelato.script.core.ScriptEngineBuilder;
import cn.geelato.script.core.ScriptProfile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 判定管道引擎单元测试（纯脚本形态：内置 SQL/函数 kit 已从 SDK 移除，
 * SQL 场景待消费方 kit 回归后补回；本类覆盖三态触发、$ctx.bizId 注入、
 * $input 链、$params 注入与 hits 契约）。
 */
class PipelineEngineTest {

    private static DefaultScriptEngine scriptEngine;
    private static PipelineEngine pipelineEngine;

    @BeforeAll
    static void init() {
        scriptEngine = new ScriptEngineBuilder()
                .profile(ScriptProfile.builder()
                        .id(PipelineEngine.PROFILE_ID)
                        .namespaces(Set.of(ScriptProfile.ALL))
                        .variables(Set.of(ScriptProfile.ALL))
                        .defaultTimeoutMs(5_000)
                        .build())
                .build();
        pipelineEngine = new PipelineEngine(scriptEngine, 5_000, 10_000, 5_000);
    }

    @AfterAll
    static void shutdown() {
        scriptEngine.close();
    }

    private static FmsExceptionRule ruleOf(String pipelineJson) {
        FmsExceptionRule rule = new FmsExceptionRule();
        rule.setRuleCode("EXC-TEST-001");
        rule.setRuleKind("ALERT");
        rule.setLevel("P1");
        rule.setTenantCode("geelato");
        rule.setPipelineJson(pipelineJson);
        rule.setThresholdJson("{\"days\": 1}");
        return rule;
    }

    @Test
    void trialRunProducesValidHits() {
        FmsExceptionRule rule = ruleOf("{\"steps\":[{\"name\":\"judge\",\"lang\":\"js\",\"content\":"
                + "\"const rows = [{bizId: '1', amount: 500}, {bizId: '2', amount: 0}, {amount: 99}];"
                + " return rows.filter(r => r.amount > 0).map(r => ({bizType: 'BOL_GRANT', bizId: r.bizId, hitBrief: '未收款 ' + r.amount}))\"}]}");
        PipelineOutcome outcome = pipelineEngine.execute(rule, TriggerMode.TRIAL, null);
        assertEquals("SUCCESS", outcome.getStatus());
        assertEquals(1, outcome.getValidHits().size());
        assertEquals("1", String.valueOf(outcome.getValidHits().get(0).get("bizId")));
        assertEquals("v1", outcome.getEngineVersion());
    }

    @Test
    void recheckInjectsBizId() {
        FmsExceptionRule rule = ruleOf("{\"steps\":[{\"name\":\"judge\",\"lang\":\"js\",\"content\":"
                + "\"const rows = [{bizId: '1', amount: 500}, {bizId: '2', amount: 0}];"
                + " return rows.filter(r => r.bizId === $ctx.bizId && r.amount > 0).map(r => ({bizType: 'BOL_GRANT', bizId: r.bizId}))\"}]}");
        PipelineOutcome hit = pipelineEngine.execute(rule, TriggerMode.RECHECK, "1");
        PipelineOutcome miss = pipelineEngine.execute(rule, TriggerMode.RECHECK, "2");
        assertEquals(1, hit.getValidHits().size());
        assertEquals(0, miss.getValidHits().size());
    }

    @Test
    void nonArrayOutputIsContractFatal() {
        FmsExceptionRule rule = ruleOf("{\"steps\":[{\"name\":\"judge\",\"lang\":\"js\",\"content\":\"return 'oops'\"}]}");
        PipelineOutcome outcome = pipelineEngine.execute(rule, TriggerMode.TRIAL, null);
        assertEquals("CONTRACT_FATAL", outcome.getStatus());
        assertTrue(outcome.getValidHits().isEmpty());
    }

    @Test
    void multiStepPipelinePassesInputBetweenSteps() {
        FmsExceptionRule rule = ruleOf("{\"steps\":["
                + "{\"name\":\"fetch\",\"lang\":\"js\",\"content\":\"return [{bizId: '1', amount: 500}, {bizId: '2', amount: 0}]\"},"
                + "{\"name\":\"judge\",\"lang\":\"js\",\"content\":\"return $input.filter(r => r.amount > 0).map(r => ({bizType: 'BOL_GRANT', bizId: r.bizId}))\"}"
                + "]}");
        PipelineOutcome outcome = pipelineEngine.execute(rule, TriggerMode.SCAN, null);
        assertEquals("SUCCESS", outcome.getStatus());
        assertEquals(1, outcome.getValidHits().size());
        assertEquals(2, outcome.getSteps().size());
        assertEquals("fetch", outcome.getSteps().get(0).get("name"));
        assertEquals("judge", outcome.getSteps().get(1).get("name"));
    }

    @Test
    void brokenScriptIsStepFailed() {
        FmsExceptionRule rule = ruleOf("{\"steps\":[{\"name\":\"judge\",\"lang\":\"js\",\"content\":\"return nope_undefined_fn()\"}]}");
        PipelineOutcome outcome = pipelineEngine.execute(rule, TriggerMode.TRIAL, null);
        assertEquals("STEP_FAILED", outcome.getStatus());
        assertTrue(outcome.getError() != null && outcome.getError().contains("nope"));
        assertEquals("ERROR", outcome.getSteps().get(0).get("status"));
    }

    @Test
    void paramsInjectedAndUsable() {
        FmsExceptionRule rule = ruleOf("{\"steps\":[{\"name\":\"judge\",\"lang\":\"js\",\"content\":"
                + "\"const rows = [{bizId: '1', amount: 500}, {bizId: '2', amount: 50}];"
                + " return rows.filter(r => r.amount > $params.minAmount).map(r => ({bizType: 'X', bizId: r.bizId, hitBrief: '超阈值 ' + r.amount}))\"}]}");
        rule.setThresholdJson("{\"minAmount\": 100}");
        PipelineOutcome outcome = pipelineEngine.execute(rule, TriggerMode.TRIAL, null);
        assertEquals("SUCCESS", outcome.getStatus());
        assertEquals(1, outcome.getValidHits().size());
        assertEquals("超阈值 500", outcome.getValidHits().get(0).get("hitBrief"));
    }
}
