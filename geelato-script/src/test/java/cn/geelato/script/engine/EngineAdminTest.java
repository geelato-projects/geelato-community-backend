package cn.geelato.script.engine;

import cn.geelato.core.graal.GraalFunction;
import cn.geelato.core.graal.GraalService;
import cn.geelato.script.kit.KitContext;
import cn.geelato.script.core.DefaultScriptEngine;
import cn.geelato.script.core.EngineAdmin;
import cn.geelato.script.core.EngineRevision;
import cn.geelato.script.core.ScriptEngineBuilder;
import cn.geelato.script.core.ScriptExecution;
import cn.geelato.script.core.ScriptResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineAdminTest {

    @GraalService(name = "counter", built = "true", descrption = "版本化 kit V1（单例）")
    public static class CounterV1 {
        @GraalFunction(example = "$gl.counter.tag()", description = "版本标记")
        public String tag() {
            return "c1";
        }
    }

    @GraalService(name = "counter", built = "true", descrption = "版本化 kit V2（scoped）")
    public static class CounterV2 {
        public CounterV2(KitContext ctx) {
        }

        @GraalFunction(example = "$gl.counter.tag()", description = "版本标记")
        public String tag() {
            return "c2";
        }
    }

    @GraalService(name = "extra", built = "true", descrption = "v3 移除对象")
    public static class ExtraKit {
        @GraalFunction(example = "$gl.extra.ping()", description = "探针")
        public String ping() {
            return "pong";
        }
    }

    private static final DefaultScriptEngine engine = buildEngine();

    private static DefaultScriptEngine buildEngine() {
        DefaultScriptEngine e = new ScriptEngineBuilder()
                .registerKit(CounterV1.class)
                .registerKit(new ExtraKit())
                .deriveRevision("v2", null, d -> d.scoped("counter", CounterV2.class))
                .defaultVersion("v1")
                .build();
        // v3：移除 extra（运行时派生注册路径）；vlc：弃用/下线生命周期专用
        e.registerRevision(e.deriveRevision("v3", "v1", d -> d.remove("extra")));
        e.registerRevision(e.deriveRevision("vlc", "v1", d -> {
        }));
        return e;
    }

    @AfterAll
    static void shutdown() {
        engine.close();
    }

    private static ScriptResult run(String content, String version) {
        return engine.execute(ScriptExecution.builder()
                .content(content).engineVersion(version)
                .profileId("default").build());
    }

    @Test
    void deriveRevisionSwapsKitImplementationPerVersion() {
        assertEquals("SUCCESS", run("return $gl.counter.tag()", "v1").getStatus());
        assertEquals("c1", run("return $gl.counter.tag()", "v1").getValue());
        assertEquals("c2", run("return $gl.counter.tag()", "v2").getValue());
        assertEquals("v2", run("return $gl.counter.tag()", "v2").getEngineVersion());
        // 换版本不影响旧版本快照
        assertEquals("c1", run("return $gl.counter.tag()", "v1").getValue());
    }

    @Test
    void deriveRevisionRemovesNamespaceOnlyInDerivedVersion() {
        assertEquals("pong", run("return $gl.extra.ping()", "v1").getValue());
        assertEquals("gone", run("return typeof $gl.extra === 'undefined' ? 'gone' : 'leaked'", "v3").getValue());
        assertEquals("pong", run("return $gl.extra.ping()", "v1").getValue());
    }

    @Test
    void kitCatalogFiltersByRevision() {
        assertTrue(engine.kitCatalog("default", "v1").stream().anyMatch(s -> "extra".equals(s.getServiceName())));
        assertFalse(engine.kitCatalog("default", "v3").stream().anyMatch(s -> "extra".equals(s.getServiceName())));
        assertTrue(engine.kitCatalog("default", "v2").stream().anyMatch(s -> "counter".equals(s.getServiceName())));
    }

    @Test
    void runtimeDeriveInheritsEsVersionAndBaselineSnapshot() {
        EngineRevision v4 = engine.deriveRevision("v4", "v2", d -> d.esVersion("2021"));
        assertEquals("2021", v4.getEsVersion());
        assertEquals("2022", engine.deriveRevision("vX", "v1", d -> {
        }).getEsVersion());
        assertEquals(CounterV2.class, v4.getScopedKits().get("counter").kitClass());
    }

    @Test
    void registerRevisionAndDuplicateRejected() {
        EngineRevision v5 = engine.deriveRevision("v5", "v1", d -> d.singleton("counter", new CounterV1()));
        assertEquals("v5", engine.registerRevision(v5).getId());
        assertEquals("c1", run("return $gl.counter.tag()", "v5").getValue());
        assertThrows(IllegalArgumentException.class,
                () -> engine.registerRevision(engine.deriveRevision("v5", "v1", d -> {
                })));
    }

    @Test
    void setDefaultSwitchesHotWithoutRestart() {
        assertEquals("v1", run("return 1", null).getEngineVersion());
        engine.setDefault("v2");
        try {
            assertEquals("v2", run("return 1", null).getEngineVersion());
        } finally {
            engine.setDefault("v1");
        }
        assertEquals("v1", run("return 1", null).getEngineVersion());
    }

    @Test
    void runtimeDeprecateAndRetireLifecycle() {
        assertFalse(run("return 1", "vlc").getDeprecationWarning() != null
                && !run("return 1", "vlc").getDeprecationWarning().isEmpty());

        assertEquals("DEPRECATED", engine.deprecate("vlc", "请迁移到 v1").getStatus());
        ScriptResult deprecated = run("return 1", "vlc");
        assertEquals("SUCCESS", deprecated.getStatus());
        assertNotNull(deprecated.getDeprecationWarning());
        assertTrue(deprecated.getConsole().stream().anyMatch(l -> l.contains("已标记弃用")));

        assertEquals("RETIRED", engine.retire("vlc").getStatus());
        assertThrows(IllegalArgumentException.class, () -> run("return 1", "vlc"));
        assertThrows(IllegalArgumentException.class, () -> engine.setDefault("vlc"));
        assertThrows(IllegalArgumentException.class, () -> engine.deprecate("vlc", "再弃用"));
        assertEquals("RETIRED", engine.versions().stream()
                .filter(v -> "vlc".equals(v.getId())).findFirst().orElseThrow().getStatus());
    }

    @Test
    void retireRequiresDeprecateFirst() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> engine.retire("v3"));
        assertTrue(ex.getMessage().contains("先标记弃用"));
    }

    @Test
    void deprecateRequiresOneCurrentRemaining() {
        DefaultScriptEngine single = new ScriptEngineBuilder().defaultVersion("only").build();
        try {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> single.deprecate("only", "无人接替"));
            assertTrue(ex.getMessage().contains("CURRENT"));
        } finally {
            single.close();
        }
    }

    @Test
    void versionsReflectRuntimeState() {
        assertTrue(engine.versions().stream().anyMatch(v -> "v3".equals(v.getId())));
        assertEquals("CURRENT", engine.versions().stream()
                .filter(v -> "v1".equals(v.getId())).findFirst().orElseThrow().getStatus());
    }

    @Test
    void deriveFromUnknownBaselineRejectedAtBuild() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new ScriptEngineBuilder()
                        .deriveRevision("v9", "nope", d -> {
                        })
                        .defaultVersion("v1")
                        .build());
        assertTrue(ex.getMessage().contains("派生基线版本不存在"));
    }
}
