package cn.geelato.script.engine;

import cn.geelato.script.core.DebugStatus;
import cn.geelato.script.core.ScriptDebugService;
import cn.geelato.script.core.ScriptEngineBuilder;
import cn.geelato.script.core.ScriptExecution;
import cn.geelato.script.lang.WasmLanguageExecutor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Debug service guards that do NOT trigger a real inspector-attached eval (a
 * suspended debug eval blocks until a DevTools client attaches, which no
 * headless test can do; that path is covered by manual acceptance). These cases
 * exercise the language gate and the unknown-session status branch.
 */
class ScriptDebugServiceTest {

    @Test
    void statusForUnknownSessionIsStopped() {
        var engine = new ScriptEngineBuilder().build();
        try {
            ScriptDebugService debug = (ScriptDebugService) engine;
            DebugStatus status = debug.status("no-such-session");
            assertNotNull(status);
            assertEquals(DebugStatus.State.STOPPED, status.getState());
            assertFalse(status.isAlive());
        } finally {
            engine.close();
        }
    }

    @Test
    void rejectsNonJavaScriptDebugBeforeCreatingSession() {
        var engine = new ScriptEngineBuilder()
                .languageExecutor(new WasmLanguageExecutor())
                .build();
        try {
            ScriptDebugService debug = (ScriptDebugService) engine;
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> debug.start(ScriptExecution.builder()
                            .lang("wasm")
                            .content("nope")
                            .build()));
            assertTrue(ex.getMessage().contains("javascript"));
            // no session registered as a side effect
            assertEquals(DebugStatus.State.STOPPED, debug.status("wasm").getState());
        } finally {
            engine.close();
        }
    }
}
