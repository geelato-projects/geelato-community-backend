package cn.geelato.script.lang;

import cn.geelato.script.core.ScriptIssue;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JS 执行器：变量以 JSON 字面量 eval 注入（受限 HostAccess 下 host Map 成员语义不可访问，
 * 脚本须拿到原生 JS 对象），用户脚本包 IIFE 取 return 值。
 */
public class JsLanguageExecutor implements ScriptLanguageExecutor {

    /** IIFE 包装引入的行号偏移：对外报告的用户行号 = GraalVM 行号 - 此值。 */
    public static final int IIFE_LINE_OFFSET = 1;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String language() {
        return "js";
    }

    @Override
    public Object execute(Context context, String content, String stepName) {
        String wrapped = "(function(){\n" + content + "\n})";
        Value fn = eval(context, wrapped, "step:" + stepName);
        Value result = fn.execute();
        return ValueUnwrapper.toJavaObject(result);
    }

    /**
     * 调试专用源码：行号与编辑器 1:1 对齐——IIFE 前缀 {@code (function(){} 内联到首行首列（不占行），
     * 仅影响第 1 行第 1 列，DevTools 行号 = 编辑器行号（无 ±1 偏移）。
     * Source 名稳定为 {@code gl-debug://<scriptName>} 以便断点持久。闭合 {@code })} 置于末行新增行，
     * 不影响用户行号对齐。
     */
    public Source debugSource(String content, String scriptName) {
        String wrapped = "(function(){" + (content == null ? "" : content) + "\n})";
        try {
            return Source.newBuilder("js", wrapped, debugSourceName(scriptName)).build();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("调试脚本源构建失败", e);
        }
    }

    /** 调试执行：在已注入 kit/bindings 的 debug Context 上 eval 对齐 Source 并调用（阻塞于断点）。 */
    public Object executeDebug(Context context, String content, String scriptName) {
        Value fn = context.eval(debugSource(content, scriptName));
        Value result = fn.execute();
        return ValueUnwrapper.toJavaObject(result);
    }

    /** 调试 Source 名（供面板/代理映射断点，行号恒定）。 */
    public static String debugSourceName(String scriptName) {
        String safe = scriptName == null || scriptName.isBlank() ? "script" : scriptName.trim();
        return "gl-debug://" + safe;
    }

    @Override
    public List<ScriptIssue> parseOnly(Context context, String content, String stepName) {
        List<ScriptIssue> issues = new ArrayList<>();
        try {
            String wrapped = "(function(){\n" + (content == null ? "" : content) + "\n})";
            context.parse(Source.newBuilder("js", wrapped, "parse:" + stepName).build());
        } catch (PolyglotException e) {
            issues.add(issue(e));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("脚本源构建失败", e);
        }
        return issues;
    }

    @Override
    public void bindVariables(Context context, Map<String, Object> variables, boolean frozen) {
        if (variables == null || variables.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : variables.entrySet()) {
            sb.append("var ").append(e.getKey()).append(" = ").append(toJson(e.getValue())).append(";\n");
            if (frozen && (e.getValue() instanceof Map || e.getValue() instanceof List)) {
                sb.append("Object.freeze(").append(e.getKey()).append(");\n");
            }
        }
        eval(context, sb.toString(), "vars");
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value == null ? "" : value);
        } catch (Exception e) {
            throw new IllegalStateException("变量注入序列化失败: " + e.getMessage(), e);
        }
    }

    private static Value eval(Context context, String script, String name) {
        try {
            return context.eval(Source.newBuilder("js", script, name).build());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("脚本源构建失败", e);
        }
    }

    private ScriptIssue issue(PolyglotException e) {
        ScriptIssue i = new ScriptIssue();
        i.setCode(e.isSyntaxError() ? "SYNTAX" : "PARSE");
        i.setMessage(e.getMessage());
        if (e.getSourceLocation() != null) {
            i.setLine(Math.max(1, e.getSourceLocation().getStartLine() - IIFE_LINE_OFFSET));
            i.setCol(e.getSourceLocation().getStartColumn());
        }
        return i;
    }
}
