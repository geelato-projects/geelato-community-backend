package cn.geelato.script.engine;

import cn.geelato.core.graal.GraalFunction;
import cn.geelato.core.graal.GraalService;
import cn.geelato.script.core.DefaultScriptEngine;
import cn.geelato.script.core.ScriptEngineBuilder;
import cn.geelato.script.core.ScriptExecution;
import cn.geelato.script.core.ScriptProfile;
import cn.geelato.script.core.ScriptResult;
import cn.geelato.security.App;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptEngineExtTest {

    @GraalService(name = "target", built = "true", descrption = "升级目标在途实例")
    public static class TargetService {

        private final cn.geelato.script.kit.KitContext ctx;

        public TargetService(cn.geelato.script.kit.KitContext ctx) {
            this.ctx = ctx;
        }

        @GraalFunction(example = "$gl.target.targetInstance('10234')", description = "按 bizId 取在途实例")
        public Object targetInstance(String bizId) {
            ctx.record(cn.geelato.script.core.TraceEntry.of("target", "targetInstance", "bizId=" + bizId, 0, 0));
            return cn.geelato.script.lang.GuestValues.toGuest(Map.of("bizId", bizId, "status", "OPEN"));
        }
    }

    private static final DefaultScriptEngine engine = buildEngine();

    private static DefaultScriptEngine buildEngine() {
        ScriptProfile full = ScriptProfile.builder()
                .id("full")
                .namespaces(Set.of(ScriptProfile.ALL))
                .variables(Set.of(ScriptProfile.ALL))
                .defaultTimeoutMs(10_000)
                .build();
        return new ScriptEngineBuilder()
                .registerKit(TargetService.class)
                .profile(full)
                .statementLimit(2_000_000_000L)
                .build();
    }

    private static User user;
    private static Tenant tenant;
    private static App app;

    @BeforeAll
    static void setUpIdentity() {
        user = new User();
        user.setUserId("u001");
        user.setUserName("张三");
        user.setLoginName("zhangsan");
        tenant = new Tenant("tenant001");

        app = new App();
        app.setId("fms");
        SecurityContext.setCurrentUser(user);
        SecurityContext.setCurrentTenant(tenant);
        SecurityContext.setCurrentApp(app);
    }

    @AfterAll
    static void shutdown() {
        SecurityContext.clear();
        engine.close();
    }

    @Test
    void envInjectedWithExecutionFacts() {
        ScriptResult result = engine.execute(ScriptExecution.builder()
                .content("return $env.lang + '/' + $env.engineVersion + '/' + $env.profileId + '/' + $env.user.userName + '/' + $env.tenantCode")
                .profileId("full")
                .build());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("js/v1/full/张三/tenant001", result.getValue());
        assertNotNull(result.getExecId());

        ScriptResult execId = engine.execute(ScriptExecution.builder()
                .content("return $env.execId").profileId("full").build());
        assertEquals(execId.getExecId(), execId.getValue());
    }

    @Test
    void envIsFrozen() {
        ScriptResult result = engine.execute(ScriptExecution.builder()
                .content("$env.execId = 'hack'; return $env.execId === 'hack' ? 'mutated' : 'frozen'")
                .profileId("full")
                .build());
        assertEquals("frozen", result.getValue());
    }

    @Test
    void paramsFieldNormalizedToDollarParams() {
        ScriptResult result = engine.execute(ScriptExecution.builder()
                .content("return $params.days + 1")
                .profileId("full")
                .params(Map.of("days", 8))
                .build());
        assertEquals(9L, ((Number) result.getValue()).longValue());
    }

    @Test
    void explicitBindingsWinOverParams() {
        ScriptResult result = engine.execute(ScriptExecution.builder()
                .content("return $params.days")
                .profileId("full")
                .params(Map.of("days", 1))
                .bindings(Map.of("$params", Map.of("days", 42)))
                .build());
        assertEquals(42L, ((Number) result.getValue()).longValue());
    }

    @Test
    void errorLineIsUserLineWithStack() {
        ScriptResult result = engine.execute(ScriptExecution.builder()
                .content("let a = 1\nlet b = ;")
                .profileId("full")
                .build());
        assertEquals("ERROR", result.getStatus());
        assertEquals("SYNTAX", result.getError().getType());
        assertEquals(2, result.getError().getLine());

        ScriptResult runtime = engine.execute(ScriptExecution.builder()
                .content("function boom() { return null.x }\nboom()")
                .profileId("full")
                .build());
        assertEquals("ERROR", runtime.getStatus());
        assertNotNull(runtime.getError().getStack());
        assertTrue(runtime.getError().getStack().size() > 0);
    }

    @Test
    void traceCollectedFromKitCalls() {
        ScriptResult result = engine.execute(ScriptExecution.builder()
                .content("return $gl.target.targetInstance('10234').status")
                .profileId("full")
                .build());
        assertEquals("SUCCESS", result.getStatus(), String.valueOf(result.getError()));
        assertTrue(result.getTrace().stream().anyMatch(t -> "target".equals(t.getNs())), String.valueOf(result.getTrace()));
    }

    @Test
    void noIdentityEnvironmentIsNullSafe() {
        SecurityContext.clear();
        try {
            ScriptResult result = engine.execute(ScriptExecution.builder()
                    .content("return String($env.user)")
                    .profileId("full")
                    .build());
            assertEquals("SUCCESS", result.getStatus());
            assertEquals("null", result.getValue());
        } finally {
            SecurityContext.setCurrentUser(user);
            SecurityContext.setCurrentTenant(tenant);
            SecurityContext.setCurrentApp(app);
        }
    }

    @Test
    void envVisibleAttributesExposed() {
        ScriptResult result = engine.execute(ScriptExecution.builder()
                .content("return $env.attrs.ruleCode")
                .profileId("full")
                .attributes(Map.of("env.ruleCode", "FIN-001", "secret.key", "hidden"))
                .build());
        assertEquals("FIN-001", result.getValue());
    }

    @Test
    void catalogIncludesRegisteredNamespaces() {
        var catalog = engine.kitCatalog("full", "v1");
        List<String> names = catalog.stream().map(c -> c.getServiceName()).toList();
        assertTrue(names.contains("target"), names.toString());
    }

    @Test
    void helpListsVisibleNamespaces() {
        ScriptResult result = engine.execute(ScriptExecution.builder()
                .content("return $gl.help()").profileId("full").build());
        assertEquals("SUCCESS", result.getStatus(), String.valueOf(result.getError()));
        String help = String.valueOf(result.getValue());
        assertTrue(help.contains("$gl.target"), help);
        assertTrue(help.contains("targetInstance"), help);
        assertTrue(help.contains("$gl.sql"), help);
        assertTrue(help.contains("$gl.help('"), help);
    }

    @Test
    void helpSingleNamespaceShowsExamples() {
        ScriptResult result = engine.execute(ScriptExecution.builder()
                .content("return $gl.help('encrypt')").profileId("full").build());
        assertEquals("SUCCESS", result.getStatus(), String.valueOf(result.getError()));
        String help = String.valueOf(result.getValue());
        assertTrue(help.contains("$gl.encrypt"), help);
        assertTrue(help.contains("$gl.encrypt.md5('text')"), help);
        assertTrue(help.contains("32 位小写十六进制"), help);
    }

    @Test
    void helpUnknownNamespaceListsAvailable() {
        ScriptResult result = engine.execute(ScriptExecution.builder()
                .content("return $gl.help('nope')").profileId("full").build());
        assertEquals("SUCCESS", result.getStatus(), String.valueOf(result.getError()));
        String help = String.valueOf(result.getValue());
        assertTrue(help.contains("未找到 kit: nope"), help);
        assertTrue(help.contains("sql"), help);
    }
}
