package cn.geelato.script.engine;

import cn.geelato.core.graal.GraalFunction;
import cn.geelato.core.graal.GraalService;
import cn.geelato.script.core.DefaultScriptEngine;
import cn.geelato.script.core.ScriptEngineBuilder;
import cn.geelato.script.core.ScriptExecution;
import cn.geelato.script.core.ScriptResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * built=false kit 顶层挂载语义：暴露名 = $ + name（name 声明不带 $），
 * 与 $gl.help()、gl.d.ts 生成器同一口径；name 已带 $ 的历史声明原样兼容。
 */
class TopLevelExposeTest {

    @GraalService(name = "plain", built = "false", descrption = "常规声明（不带 $）")
    public static class PlainKit {
        @GraalFunction(example = "$plain.tag()", description = "标记")
        public String tag() {
            return "plain";
        }
    }

    @GraalService(name = "$legacy", built = "false", descrption = "历史声明（自带 $）")
    public static class LegacyKit {
        @GraalFunction(example = "$legacy.tag()", description = "标记")
        public String tag() {
            return "legacy";
        }
    }

    private static final DefaultScriptEngine engine = new ScriptEngineBuilder()
            .registerKit(new PlainKit())
            .registerKit(new LegacyKit())
            .build();

    @AfterAll
    static void shutdown() {
        engine.close();
    }

    private static ScriptResult run(String content) {
        return engine.execute(ScriptExecution.builder().content(content).build());
    }

    @Test
    void topLevelExposedWithDollarPrefix() {
        ScriptResult result = run("return $plain.tag()");
        assertEquals("SUCCESS", result.getStatus(), String.valueOf(result.getError()));
        assertEquals("plain", result.getValue());
        assertEquals("bare-not-visible", run("return typeof plain === 'undefined' ? 'bare-not-visible' : 'leaked'").getValue());
    }

    @Test
    void legacyDollarNameExposedAsIs() {
        assertEquals("legacy", run("return $legacy.tag()").getValue());
    }

    @Test
    void helpUsesSameExposeName() {
        ScriptResult help = run("return $gl.help()");
        String text = String.valueOf(help.getValue());
        assertTrue(text.contains("$plain"), "help 应显示 $plain: " + text);
        assertTrue(text.contains("$legacy") && !text.contains("$$legacy"), "help 历史 $ 声明不双写: " + text);
    }
}
