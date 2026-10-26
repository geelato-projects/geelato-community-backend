package cn.geelato.script.engine;

import cn.geelato.script.core.DefaultScriptEngine;
import cn.geelato.script.core.ScriptEngineBuilder;
import cn.geelato.script.core.ScriptExecution;
import cn.geelato.script.core.ScriptProfile;
import cn.geelato.script.core.ScriptResult;
import cn.geelato.script.kit.KitDtsGenerator;
import cn.geelato.script.kit.KitRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 整库注入：$kit.{库别名}.{类简单名小写}.{方法}（fixture 见 cn.geelato.script.hostfixture）。 */
class HostKitTest {

    private static final DefaultScriptEngine engine = new ScriptEngineBuilder()
            .hostPackage("lib1", "cn.geelato.script.hostfixture.util")
            .hostPackage("libA", "cn.geelato.script.hostfixture.a")
            .hostPackage("libB", "cn.geelato.script.hostfixture.b")
            .profile(ScriptProfile.builder()
                    .id("noKit")
                    .namespaces(Set.of())
                    .variables(Set.of())
                    .defaultTimeoutMs(5_000)
                    .build())
            .build();

    @AfterAll
    static void shutdown() {
        engine.close();
    }

    private static ScriptResult run(String content) {
        ScriptResult result = engine.execute(ScriptExecution.builder().content(content).build());
        assertEquals("SUCCESS", result.getStatus(), String.valueOf(result.getError()));
        return result;
    }

    @Test
    void staticMethodWithArgumentConversion() {
        assertEquals(true, run("return $kit.lib1.strLikeUtil.isBlank('')").getValue());
        assertEquals(false, run("return $kit.lib1.strLikeUtil.isBlank('x')").getValue());
    }

    @Test
    void overloadAritySelection() {
        assertEquals("a-b", run("return $kit.lib1.strLikeUtil.join('a', 'b')").getValue());
        assertEquals("a+b", run("return $kit.lib1.strLikeUtil.join('a', 'b', '+')").getValue());
    }

    @Test
    void instanceMethodOnInstantiatedSingleton() {
        assertEquals(5L, run("return $kit.lib1.dateLikeService.add(2, 3)").getValue());
        assertEquals("NOW", run("return $kit.lib1.dateLikeService.now()").getValue());
    }

    @Test
    void mapReturnWrappedGuestReadable() {
        assertEquals("ab", run("return $kit.lib1.strLikeUtil.info('ab').key").getValue());
        assertEquals(2L, run("return $kit.lib1.strLikeUtil.info('ab').len").getValue());
    }

    @Test
    void incompatibleMethodsAndClassesSkipped() {
        assertEquals(true, run("return typeof $kit.lib1.strLikeUtil.varargs === 'undefined' && typeof $kit.lib1.strLikeUtil.leak === 'undefined'").getValue());
        String members = String.valueOf(run("return Object.keys($kit.lib1).sort().join(',')").getValue());
        assertEquals("dateLikeService,strLikeUtil", members);
    }

    @Test
    void crossLibrarySameSimpleNameCoexists() {
        assertEquals("a", run("return $kit.libA.dupUtil.who()").getValue());
        assertEquals("b", run("return $kit.libB.dupUtil.who()").getValue());
    }

    @Test
    void helpCatalogListsFlattenedAccess() {
        String help = String.valueOf(run("return $gl.help('kit')").getValue());
        assertTrue(help.contains("lib1.strLikeUtil.isBlank"), help);
        assertTrue(help.contains("示例: lib1.strLikeUtil.join(arg0, arg1)"), help);
    }

    @Test
    void profileNamespaceFiltersWholeKit() {
        ScriptResult result = engine.execute(ScriptExecution.builder()
                .content("return typeof $kit").profileId("noKit").build());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("undefined", result.getValue());
    }

    @Test
    void excludesRemoveClass() {
        DefaultScriptEngine excluded = new ScriptEngineBuilder()
                .hostPackage("lib1", "cn.geelato.script.hostfixture.util")
                .hostExcludes(List.of("DateLikeService"))
                .build();
        try {
            ScriptResult result = excluded.execute(ScriptExecution.builder()
                    .content("return Object.keys($kit.lib1).join(',')").build());
            assertEquals("SUCCESS", result.getStatus());
            assertEquals("strLikeUtil", result.getValue());
        } finally {
            excluded.close();
        }
    }

    @Test
    void libraryNameClashFailsFast() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                new ScriptEngineBuilder().hostPackage("root", "cn.geelato.script.hostfixture").build());
        assertTrue(ex.getMessage().contains("dupUtil"), ex.getMessage());
        assertTrue(ex.getMessage().contains("cn.geelato.script.hostfixture.b.DupUtil"), ex.getMessage());
    }

    @Test
    void missingPackageFailsFast() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                new ScriptEngineBuilder().hostPackage("ghost", "cn.geelato.not.exists").build());
        assertTrue(ex.getMessage().contains("未扫到任何可注入类"), ex.getMessage());
    }

    @Test
    void dtsGeneratesNestedDeclarations() {
        KitRegistry registry = new KitRegistry();
        registry.scanHostPackages(Map.of("lib1", "cn.geelato.script.hostfixture.util"), List.of());
        String dts = KitDtsGenerator.generate(registry);
        assertTrue(dts.contains("declare const $kit: {"), dts);
        assertTrue(dts.contains("  lib1: {"), dts);
        assertTrue(dts.contains("    strLikeUtil: {"), dts);
        assertTrue(dts.contains("isBlank(s: string): boolean;"), dts);
        assertFalse(dts.contains("  kit: {"), dts);
    }
}
