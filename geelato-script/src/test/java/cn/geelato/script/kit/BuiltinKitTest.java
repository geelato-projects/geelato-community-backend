package cn.geelato.script.kit;

import cn.geelato.core.graal.GraalFunction;
import cn.geelato.core.graal.GraalService;
import cn.geelato.script.core.DefaultScriptEngine;
import cn.geelato.script.core.ScriptEngineBuilder;
import cn.geelato.script.core.ScriptExecution;
import cn.geelato.script.core.ScriptProfile;
import cn.geelato.script.core.ScriptResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuiltinKitTest {

    @GraalService(name = "sql", built = "true", descrption = "同名覆盖用测试 kit")
    public static class OverrideSqlKit {
        @GraalFunction(example = "$gl.sql.marker()", description = "覆盖标记")
        public String marker() {
            return "overridden";
        }
    }

    private static DefaultScriptEngine plainEngine;
    private static DefaultScriptEngine overrideEngine;
    private static DefaultScriptEngine narrowEngine;

    @BeforeAll
    static void init() {
        plainEngine = new ScriptEngineBuilder().build();
        overrideEngine = new ScriptEngineBuilder().registerKit(new OverrideSqlKit()).build();
        ScriptProfile narrow = ScriptProfile.builder()
                .id("narrow").namespaces(Set.of("encrypt")).variables(Set.of(ScriptProfile.ALL))
                .defaultTimeoutMs(5_000).build();
        narrowEngine = new ScriptEngineBuilder().profile(narrow).build();
    }

    @AfterAll
    static void shutdown() {
        plainEngine.close();
        overrideEngine.close();
        narrowEngine.close();
    }

    @Test
    void builtinsRegisteredByDefault() {
        ScriptResult result = plainEngine.execute(ScriptExecution.builder()
                .content("return [typeof $gl.sql, typeof $gl.mql, typeof $gl.http, typeof $gl.encrypt, typeof $gl.ctx, typeof $gl.parameter].join(',')")
                .build());
        assertEquals("SUCCESS", result.getStatus(), String.valueOf(result.getError()));
        assertEquals("object,object,object,object,object,object", result.getValue());
    }

    @Test
    void builtinCallableWithoutExplicitSetup() {
        ScriptResult result = plainEngine.execute(ScriptExecution.builder()
                .content("return $gl.encrypt.md5('abc') + '/' + $gl.parameter.get('k')")
                .params(java.util.Map.of("k", "v"))
                .build());
        assertEquals("SUCCESS", result.getStatus(), String.valueOf(result.getError()));
        assertEquals("900150983cd24fb0d6963f7d28e17f72/v", result.getValue());
    }

    @Test
    void explicitRegistrationWinsOverBuiltin() {
        ScriptResult result = overrideEngine.execute(ScriptExecution.builder()
                .content("return $gl.sql.marker()").build());
        assertEquals("SUCCESS", result.getStatus(), String.valueOf(result.getError()));
        assertEquals("overridden", result.getValue());
    }

    @Test
    void namespacesWhitelistIsolatesBuiltins() {
        ScriptResult result = narrowEngine.execute(ScriptExecution.builder()
                .content("return (typeof $gl.sql === 'undefined' ? 'ok' : 'leaked') + '/' + $gl.encrypt.md5Short('abc')")
                .profileId("narrow")
                .build());
        assertEquals("SUCCESS", result.getStatus(), String.valueOf(result.getError()));
        assertEquals("ok/3cd24fb0d6963f7d", result.getValue());
    }
}
