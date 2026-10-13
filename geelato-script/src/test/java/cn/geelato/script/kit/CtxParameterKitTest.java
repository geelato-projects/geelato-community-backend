package cn.geelato.script.kit;

import cn.geelato.script.core.DefaultScriptEngine;
import cn.geelato.script.core.ScriptEngineBuilder;
import cn.geelato.script.core.ScriptExecution;
import cn.geelato.script.core.ScriptProfile;
import cn.geelato.script.core.ScriptResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CtxParameterKitTest {

    private static DefaultScriptEngine allEngine;
    private static DefaultScriptEngine narrowVarsEngine;

    @BeforeAll
    static void init() {
        ScriptProfile narrow = ScriptProfile.builder()
                .id("narrow").namespaces(Set.of(ScriptProfile.ALL)).variables(Set.of("$input"))
                .defaultTimeoutMs(5_000).build();
        allEngine = new ScriptEngineBuilder().build();
        narrowVarsEngine = new ScriptEngineBuilder().profile(narrow).build();
    }

    @AfterAll
    static void shutdown() {
        allEngine.close();
        narrowVarsEngine.close();
    }

    @Test
    void kitReadsSameSourceAsBindings() {
        ScriptResult result = allEngine.execute(ScriptExecution.builder()
                .content("return $gl.ctx.get('bizId') + '/' + $gl.parameter.get('days') + '/' + ($ctx.bizId === $gl.ctx.get('bizId'))")
                .bindings(Map.of("$ctx", Map.of("bizId", "B-100", "trigger", "SCAN")))
                .params(Map.of("days", 30))
                .build());
        assertEquals("SUCCESS", result.getStatus(), String.valueOf(result.getError()));
        assertEquals("B-100/30/true", result.getValue());
    }

    @Test
    void allReturnsFullSnapshot() {
        ScriptResult result = allEngine.execute(ScriptExecution.builder()
                .content("const c = $gl.ctx.all(); return Object.keys(c).sort().join(',') + '|' + $gl.parameter.all().days")
                .bindings(Map.of("$ctx", Map.of("bizId", "1", "trigger", "RECHECK")))
                .params(Map.of("days", 7))
                .build());
        assertEquals("SUCCESS", result.getStatus(), String.valueOf(result.getError()));
        assertEquals("bizId,trigger|7", result.getValue());
    }

    @Test
    void missingContextYieldsEmpty() {
        ScriptResult result = allEngine.execute(ScriptExecution.builder()
                .content("return $gl.ctx.get('none') + '/' + Object.keys($gl.parameter.all()).length")
                .build());
        assertEquals("SUCCESS", result.getStatus(), String.valueOf(result.getError()));
        assertEquals("null/0", result.getValue());
    }

    @Test
    void kitNotAffectedByVariablesWhitelist() {
        // narrow profile 的 variables 白名单不含 $ctx/$params → bindings 通道被过滤，
        // 但 kit 通道（$gl.ctx/$gl.parameter）同源可读——kit 形态不受 variables 约束
        ScriptResult result = narrowVarsEngine.execute(ScriptExecution.builder()
                .content("return (typeof $ctx === 'undefined' ? 'no-var' : 'leaked') + '/' + $gl.ctx.get('bizId')")
                .profileId("narrow")
                .bindings(Map.of("$ctx", Map.of("bizId", "K-9")))
                .build());
        assertEquals("SUCCESS", result.getStatus(), String.valueOf(result.getError()));
        assertEquals("no-var/K-9", result.getValue());
    }

    @Test
    void paramsConvenienceFieldFeedsKit() {
        ScriptResult result = allEngine.execute(ScriptExecution.builder()
                .content("return $gl.parameter.get('minAmount')")
                .params(Map.of("minAmount", 100))
                .build());
        assertEquals("SUCCESS", result.getStatus(), String.valueOf(result.getError()));
        assertEquals(100L, ((Number) result.getValue()).longValue());
    }
}
