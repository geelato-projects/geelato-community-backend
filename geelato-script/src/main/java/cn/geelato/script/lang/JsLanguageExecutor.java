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
