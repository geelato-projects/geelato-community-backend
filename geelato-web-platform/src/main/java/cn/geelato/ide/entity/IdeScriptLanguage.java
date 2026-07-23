package cn.geelato.ide.entity;

/**
 * IDE 脚本支持的语言。
 * <p>
 * js     - GraalJS（默认，轻量业务编排）
 * python - GraalPython（数据/AI 生态）
 * wasm   - GraalWasm（强沙箱、高性能、不可信代码）
 *
 * @author geelato
 */
public final class IdeScriptLanguage {
    public static final String JS = "js";
    public static final String PYTHON = "python";
    public static final String WASM = "wasm";

    private IdeScriptLanguage() {
    }

    public static boolean isValid(String language) {
        return JS.equals(language) || PYTHON.equals(language) || WASM.equals(language);
    }
}
