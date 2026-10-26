package cn.geelato.script.lang;

import cn.geelato.script.core.DefaultScriptEngine;
import cn.geelato.script.core.ScriptEngineBuilder;
import cn.geelato.script.core.ScriptExecution;
import cn.geelato.script.core.ScriptResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WasmLanguageExecutorTest {

    private static final DefaultScriptEngine engine = new ScriptEngineBuilder()
            .languageExecutor(new WasmLanguageExecutor())
            .build();

    @AfterAll
    static void shutdown() {
        engine.close();
    }

    @Test
    void loadsEmptyModule() {
        String content = Base64.getEncoder().encodeToString(new byte[]{0, 'a', 's', 'm', 1, 0, 0, 0});
        ScriptResult result = engine.execute(ScriptExecution.builder().lang("wasm").content(content).build());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("wasm module loaded (no main exports)", result.getValue());
    }

    @Test
    void rejectsBlankContent() {
        ScriptResult result = engine.execute(ScriptExecution.builder().lang("wasm").content("  ").build());
        assertEquals("ERROR", result.getStatus());
        assertTrue(result.getError().getMessage().contains("base64"));
    }

    @Test
    void rejectsInvalidBase64() {
        ScriptResult result = engine.execute(ScriptExecution.builder().lang("wasm").content("!!!not-base64!!!").build());
        assertEquals("ERROR", result.getStatus());
        assertTrue(result.getError().getMessage().contains("解码失败"));
    }

    @Test
    void rejectsInvalidModuleBytes() {
        ScriptResult result = engine.execute(ScriptExecution.builder().lang("wasm")
                .content(Base64.getEncoder().encodeToString("not a wasm module".getBytes())).build());
        assertEquals("ERROR", result.getStatus());
        assertNotNull(result.getError().getMessage());
    }

    @Test
    void parseOnlyReportsLoadIssue() {
        var issues = engine.parseOnly("wasm", "  ", null);
        assertEquals(1, issues.size());
        assertEquals("LOAD", issues.get(0).getCode());
    }
}
