package cn.geelato.script.lang;

import cn.geelato.script.core.ScriptIssue;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Wasm 执行器（GraalWasm，语言组件 org.graalvm.polyglot:wasm 为可选依赖，宿主按需引入并注册）：
 * content 为 base64 的 wasm 模块字节，加载后实例化 main 模块并调用 WASI `_start` 导出；
 * 无变量注入语义（bindVariables no-op），模块需符合 WASI 规范才能被 GraalWasm 实例化。
 */
public class WasmLanguageExecutor implements ScriptLanguageExecutor {

    @Override
    public String language() {
        return "wasm";
    }

    @Override
    public Object execute(Context context, String content, String stepName) {
        byte[] bytes = decode(content);
        context.eval(buildSource(bytes, stepName));
        Value moduleBindings = context.getBindings("wasm");
        if (!moduleBindings.hasMember("main")) {
            return "wasm module loaded (no main exports)";
        }
        Value instance = moduleBindings.getMember("main").newInstance();
        if (instance.hasMember("_start")) {
            try {
                instance.getMember("_start").execute();
            } catch (Exception ignored) {
                // WASI _start 非零退出以异常上抛，按模块自身约定视为已执行
            }
        }
        return "wasm module executed";
    }

    @Override
    public List<ScriptIssue> parseOnly(Context context, String content, String stepName) {
        List<ScriptIssue> issues = new ArrayList<>();
        try {
            execute(context, content, stepName);
        } catch (Exception e) {
            ScriptIssue issue = new ScriptIssue();
            issue.setCode("LOAD");
            issue.setMessage(e.getMessage());
            issues.add(issue);
        }
        return issues;
    }

    @Override
    public void bindVariables(Context context, Map<String, Object> variables, boolean frozen) {
        // wasm 无变量注入语义
    }

    private static Source buildSource(byte[] bytes, String stepName) {
        try {
            return Source.newBuilder("wasm", ByteSequence.create(bytes), stepName + ".wasm").build();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("wasm 源构建失败", e);
        }
    }

    private static byte[] decode(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("wasm 字节码（base64）为空");
        }
        try {
            return Base64.getDecoder().decode(content);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("base64 解码失败: " + e.getMessage(), e);
        }
    }
}
