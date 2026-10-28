package cn.geelato.alert.engine;

import cn.geelato.script.core.DefaultScriptEngine;
import cn.geelato.script.core.ScriptEngineBuilder;
import cn.geelato.script.core.ScriptExecution;
import cn.geelato.script.core.ScriptProfile;
import cn.geelato.script.core.ScriptResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertRenderEngineTest {

    private static final DefaultScriptEngine engine = buildEngine();

    private static DefaultScriptEngine buildEngine() {
        DefaultScriptEngine e = new ScriptEngineBuilder()
                .profile(ScriptProfile.builder()
                        .id("alert-render")
                        .namespaces(Set.of(ScriptProfile.ALL))
                        .variables(Set.of(ScriptProfile.ALL))
                        .frozenBindings(true)
                        .defaultTimeoutMs(2_000)
                        .build())
                .registerKit(new TplService("https://fms.example.com/workbench"))
                .defaultVersion("v1")
                .build();
        e.registerRevision(e.deriveRevision("v0", null, d -> d.status(
                cn.geelato.script.core.EngineRevision.Status.DEPRECATED, "请迁移到 v1 模板契约")));
        return e;
    }

    @AfterAll
    static void shutdown() {
        engine.close();
    }

    @Test
    void rendersTitleAndContentWithDomainDict() {
        Map<String, Object> ctx = Map.of(
                "rule", Map.of("alertCode", "ALR-001", "time", "2026-09-29 10:00:00"));
        Map<String, Object> source = Map.of("id", "9001", "level", "P0", "domainCode", "FIN",
                "bizLabel", "HQL001", "title", "未收款 500 元", "anchorElapsed", 94320);
        ScriptResult title = engine.execute(ScriptExecution.builder()
                .content("return $tpl.levelTag($source.level) + ' ' + $source.title")
                .profileId("alert-render").bindings(Map.of("$ctx", ctx, "$source", source)).build());
        assertEquals("SUCCESS", title.getStatus());
        assertEquals("[P0 严重] 未收款 500 元", title.getValue());

        ScriptResult content = engine.execute(ScriptExecution.builder()
                .content("return $tpl.domainName($source.domainCode) + ' / ' + $tpl.fmtDuration($source.anchorElapsed)"
                        + " + ' / ' + $tpl.workbenchUrl($source.id)")
                .profileId("alert-render").bindings(Map.of("$ctx", ctx, "$source", source)).build());
        assertEquals("SUCCESS", content.getStatus());
        assertEquals("资金收付 / 26 小时 12 分 / https://fms.example.com/workbench/9001", content.getValue());
    }

    @Test
    void sourceSegmentPromotedToStandaloneBinding() {
        Map<String, Object> ctx = new java.util.HashMap<>(Map.of(
                "rule", Map.of("alertCode", "ALR-001"),
                "source", Map.of("id", "9001", "level", "P0")));
        cn.geelato.alert.engine.render.VariantScripts.RenderResult r =
                cn.geelato.alert.engine.render.VariantScripts.run(engine, "TPL-1", null,
                        "return $source.level",
                        "return typeof $ctx.source === 'undefined' ? 'ctx-clean' : 'ctx-leaked'",
                        ctx);
        assertNull(r.getError());
        assertEquals("P0", r.getTitle());
        assertEquals("ctx-clean", r.getContent());
    }

    @Test
    void groupShapeHasNoSourceBinding() {
        Map<String, Object> ctx = new java.util.HashMap<>(Map.of(
                "rule", Map.of("alertCode", "ALR-001"),
                "alerts", List.of()));
        cn.geelato.alert.engine.render.VariantScripts.RenderResult r =
                cn.geelato.alert.engine.render.VariantScripts.run(engine, "TPL-1", null,
                        "return typeof $source === 'undefined' ? 'absent' : 'leaked'",
                        "return $ctx.alerts.length + '条'",
                        ctx);
        assertNull(r.getError());
        assertEquals("absent", r.getTitle());
        assertEquals("0条", r.getContent());
    }

    @Test
    void groupContextRendersTable() {
        Map<String, Object> ctx = Map.of(
                "alerts", List.of(
                        Map.of("bizLabel", "HQL001", "level", "P0", "amountLocal", 100.5),
                        Map.of("bizLabel", "HQL002", "level", "P1", "amountLocal", 200)));
        ScriptResult result = engine.execute(ScriptExecution.builder()
                .content("return $tpl.tableRows($ctx.alerts, [{key:'bizLabel',label:'业务标识'},{key:'amountLocal',label:'金额',fmt:'AMOUNT'}])")
                .profileId("alert-render").bindings(Map.of("$ctx", ctx)).build());
        assertEquals("SUCCESS", result.getStatus());
        String md = String.valueOf(result.getValue());
        assertTrue(md.contains("| 业务标识 | 金额 |"), md);
        assertTrue(md.contains("| HQL001 | ¥100.50 |"), md);
    }

    @Test
    void allRegisteredKitsVisible() {
        ScriptResult result = engine.execute(ScriptExecution.builder()
                .content("return (typeof $tpl === 'object' && typeof $gl.encrypt === 'object') ? 'all-visible' : 'missing'")
                .profileId("alert-render").bindings(Map.of()).build());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("all-visible", result.getValue());
    }

    @Test
    void frozenCtxRejectMutation() {
        ScriptResult result = engine.execute(ScriptExecution.builder()
                .content("$ctx.rule = 1; return typeof $ctx.rule === 'object' ? 'frozen' : 'mutated'")
                .profileId("alert-render")
                .bindings(Map.of("$ctx", new java.util.HashMap<>(Map.of("rule", Map.of()))))
                .build());
        assertEquals("frozen", result.getValue());
    }

    @Test
    void brokenScriptReturnsErrorNotException() {
        ScriptResult result = engine.execute(ScriptExecution.builder()
                .content("return $tpl.no_such_helper()")
                .profileId("alert-render").bindings(Map.of("$ctx", Map.of())).build());
        assertEquals("ERROR", result.getStatus());
        assertNull(result.getValue());
        assertTrue(result.getError() != null);
    }

    @Test
    void templateEngineVersionRoutesAndWarns() {
        ScriptResult v0 = engine.execute(ScriptExecution.builder()
                .content("return $tpl.levelTag('P1')")
                .profileId("alert-render").engineVersion("v0")
                .bindings(Map.of("$ctx", Map.of())).build());
        assertEquals("SUCCESS", v0.getStatus());
        assertEquals("v0", v0.getEngineVersion());
        assertTrue(v0.getDeprecationWarning() != null && v0.getDeprecationWarning().contains("v0"));

        ScriptResult def = engine.execute(ScriptExecution.builder()
                .content("return $tpl.levelTag('P1')")
                .profileId("alert-render")
                .bindings(Map.of("$ctx", Map.of())).build());
        assertEquals("v1", def.getEngineVersion());
        assertNull(def.getDeprecationWarning());
    }
}
