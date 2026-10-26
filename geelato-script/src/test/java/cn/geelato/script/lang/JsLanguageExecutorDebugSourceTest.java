package cn.geelato.script.lang;

import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Debug line alignment: debug Source makes DevTools line number = editor line
 * number (no IIFE +/-1 offset). Pure string/Source building, no Context.
 */
class JsLanguageExecutorDebugSourceTest {

    private final JsLanguageExecutor executor = new JsLanguageExecutor();

    @Test
    void sourceNameIsStableForBreakpointPersistence() {
        assertEquals("gl-debug://step:calc", executor.debugSourceName("step:calc"));
        assertEquals("gl-debug://script", executor.debugSourceName(null));
        assertEquals("gl-debug://script", executor.debugSourceName("   "));
    }

    @Test
    void debugSourceAlignsEditorLineNumbersOneToOne() {
        // Editor has two lines: line 1 `var a = 1;`, line 2 `var b = 2;`.
        String content = "var a = 1;\nvar b = 2;";
        Source source = executor.debugSource(content, "calc");
        String[] lines = source.getCharacters().toString().split("\n", -1);
        // IIFE prefix inlined at line 1 column 1 (does not consume a line), so
        // editor content line 1 maps to Source line 1 (offset 0).
        assertTrue(lines[0].contains("var a = 1;"),
                "editor line 1 should map to Source line 1, actual: " + lines[0]);
        assertTrue(lines[1].contains("var b = 2;"),
                "editor line 2 should map to Source line 2, actual: " + lines[1]);
        // Closing `})` lands on a new trailing line, not affecting user alignment.
        assertEquals("})", lines[2].trim());
    }

    @Test
    void debugSourceHandlesNullContent() {
        Source source = executor.debugSource(null, "empty");
        assertEquals("gl-debug://empty", source.getName());
        String first = source.getCharacters().toString().split("\n", -1)[0];
        // Empty content still yields the wrapper shell starting with `(function(){`.
        assertTrue(first.startsWith("(function(){"));
    }

    @Test
    void normalExecuteKeepsPlusOneOffsetConstant() {
        assertEquals(1, JsLanguageExecutor.IIFE_LINE_OFFSET);
    }
}
