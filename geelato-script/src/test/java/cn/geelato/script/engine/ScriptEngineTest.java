package cn.geelato.script.engine;

import cn.geelato.core.graal.GraalFunction;
import cn.geelato.core.graal.GraalService;
import cn.geelato.script.core.DefaultScriptEngine;
import cn.geelato.script.core.EngineRevision;
import cn.geelato.script.core.ScriptEngineBuilder;
import cn.geelato.script.core.ScriptExecution;
import cn.geelato.script.core.ScriptProfile;
import cn.geelato.script.core.ScriptResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptEngineTest {

    @GraalService(name = "target", built = "true", descrption = "升级目标在途实例")
    public static class TargetService {
        @GraalFunction(example = "$gl.target.targetInstance('10234')", description = "按 bizId 取在途实例")
        public Object targetInstance(String bizId) {
            return cn.geelato.script.lang.GuestValues.toGuest(Map.of("bizId", bizId, "status", "OPEN"));
        }
    }

    private static final DefaultScriptEngine engine = buildEngine();

    private static DefaultScriptEngine buildEngine() {
        ScriptProfile judge = ScriptProfile.builder()
                .id("judge")
                .namespaces(Set.of("target"))
                .variables(Set.of(ScriptProfile.ALL))
                .defaultTimeoutMs(5_000)
                .build();
        ScriptProfile render = ScriptProfile.builder()
                .id("render")
                .namespaces(Set.of())
                .variables(Set.of("$ctx"))
                .frozenBindings(true)
                .defaultTimeoutMs(2_000)
                .build();
        ScriptProfile legacy = ScriptProfile.builder()
                .id("legacy")
                .namespaces(Set.of(ScriptProfile.ALL))
                .variables(Set.of(ScriptProfile.ALL))
                .defaultTimeoutMs(5_000)
                .build();
        return new ScriptEngineBuilder()
                .registerKit(new TargetService())
                .statementLimit(2_000_000_000L)
                .profile(judge)
                .profile(render)
                .profile(legacy)
                .revision(new EngineRevision("v0", EngineRevision.Status.DEPRECATED, "请迁移到 v1", "2022",
                        Map.of(), Map.of(), Map.of()))
                .revision(new EngineRevision("vEOL", EngineRevision.Status.RETIRED, "已下线", "2022",
                        Map.of(), Map.of(), Map.of()))
                .defaultVersion("v1")
                .build();
    }

    @AfterAll
    static void shutdown() {
        engine.close();
    }

    @Test
    void executeSingleScriptWithVariables() {
        ScriptResult result = engine.execute(ScriptExecution.builder()
                .content("return $ctx.rule.code + '/' + $params.days")
                .profileId("judge")
                .bindings(Map.of("$ctx", Map.of("rule", Map.of("code", "EXC-BL-005")), "$params", Map.of("days", 90)))
                .build());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("EXC-BL-005/90", result.getValue());
        assertEquals("v1", result.getEngineVersion());
    }

    @Test
    void targetKitRegisteredByConsumer() {
        ScriptResult result = engine.execute(ScriptExecution.builder()
                .content("const t = $gl.target.targetInstance('10234'); return t.status")
                .profileId("judge")
                .build());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("OPEN", result.getValue());
    }

    @Test
    void consoleCollected() {
        ScriptResult result = engine.execute(ScriptExecution.builder()
                .content("console.log('hello', 1); console.warn('careful'); return 'ok'")
                .profileId("judge")
                .build());
        assertTrue(result.getConsole().stream().anyMatch(l -> l.contains("hello 1")));
        assertTrue(result.getConsole().stream().anyMatch(l -> l.contains("[warn] careful")));
    }

    @Test
    void sandboxBlocksHostEscape() {
        ScriptResult result = engine.execute(ScriptExecution.builder()
                .content("try { Java.type('java.lang.System'); return 'escaped' } catch (e) { return 'blocked:' + (e + '').substring(0, 20) }")
                .profileId("judge")
                .build());
        assertEquals("SUCCESS", result.getStatus());
        assertTrue(String.valueOf(result.getValue()).startsWith("blocked"), String.valueOf(result.getValue()));
    }

    @Test
    void timeoutKillsInfiniteLoop() {
        ScriptResult result = engine.execute(ScriptExecution.builder()
                .content("while (true) { }")
                .profileId("judge")
                .timeoutMs(300L)
                .build());
        assertEquals("TIMEOUT", result.getStatus());
        assertNotNull(result.getError());
        assertEquals("TIMEOUT", result.getError().getType());
    }

    @Test
    void profileBlocksUnauthorizedNamespace() {
        ScriptResult result = engine.execute(ScriptExecution.builder()
                .content("return (typeof $gl === 'undefined' || typeof $gl.target === 'undefined') ? 'ok' : 'leaked'")
                .profileId("render")
                .build());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("ok", result.getValue());
    }

    @Test
    void profileBlocksUnauthorizedVariable() {
        ScriptResult result = engine.execute(ScriptExecution.builder()
                .content("return typeof $params === 'undefined' ? 'no-params' : 'leaked'")
                .profileId("render")
                .bindings(Map.of("$params", Map.of("days", 90)))
                .build());
        assertEquals("no-params", result.getValue());
    }

    @Test
    void versionRoutingDeprecationAndRetired() {
        ScriptResult deprecated = engine.execute(ScriptExecution.builder()
                .content("return 1")
                .engineVersion("v0")
                .profileId("legacy")
                .build());
        assertEquals("SUCCESS", deprecated.getStatus());
        assertEquals("v0", deprecated.getEngineVersion());
        assertNotNull(deprecated.getDeprecationWarning());

        assertThrows(IllegalArgumentException.class, () -> engine.execute(ScriptExecution.builder()
                .content("return 1")
                .engineVersion("vEOL")
                .profileId("legacy")
                .build()));
        assertThrows(IllegalArgumentException.class, () -> engine.execute(ScriptExecution.builder()
                .content("return 1")
                .engineVersion("v999")
                .profileId("legacy")
                .build()));

        assertEquals(3, engine.versions().size());
        assertTrue(engine.versions().stream().anyMatch(v -> "DEPRECATED".equals(v.getStatus()) && "v0".equals(v.getId())));
    }

    @Test
    void parseOnlyCatchesSyntaxError() {
        var issues = engine.parseOnly("js", "return {", "v1");
        assertTrue(!issues.isEmpty());
        assertEquals("SYNTAX", issues.get(0).getCode());
        assertTrue(engine.parseOnly("js", "return 1", "v1").isEmpty());
    }

    @Test
    void kitCatalogFiltersByProfile() {
        var catalog = engine.kitCatalog("judge", "v1");
        assertTrue(catalog.stream().anyMatch(s -> "target".equals(s.getServiceName())));
        var renderCatalog = engine.kitCatalog("render", "v1");
        assertTrue(renderCatalog.stream().noneMatch(s -> "target".equals(s.getServiceName())));
    }

    @Test
    void frozenBindingsRejectWrite() {
        ScriptResult result = engine.execute(ScriptExecution.builder()
                .content("$ctx.a = 1; return $ctx.a === 0 ? 'frozen' : 'mutated'")
                .profileId("render")
                .bindings(Map.of("$ctx", new java.util.HashMap<>(Map.of("a", 0))))
                .build());
        assertEquals("frozen", result.getValue());
    }

    @Test
    void concurrentExecutionsShareEngineSafely() throws Exception {
        java.util.concurrent.Callable<String> task = () -> {
            ScriptResult r = engine.execute(ScriptExecution.builder()
                    .content("let s = 0; for (let i = 0; i < 1000; i++) { s += i } return String(s)")
                    .profileId("judge")
                    .build());
            return String.valueOf(r.getValue());
        };
        var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<String>>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(task));
            }
            for (var f : futures) {
                assertEquals("499500", f.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
