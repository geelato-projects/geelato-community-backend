package cn.geelato.alert.engine.render;

import cn.geelato.script.core.ScriptEngine;
import cn.geelato.script.core.ScriptExecution;
import cn.geelato.script.core.ScriptResult;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模板变体脚本执行器（title/content 两段，alert-render profile，$ctx 只读注入）：
 * testRender 试渲染与投递引擎共用；title&lt;=120 / content&lt;=32KB 超长截断。
 * 源上下文为模板独有绑定：ctxBindings 的 source 段提升为一级 $source（$ctx 不再携带；
 * group/schedule 形态无 source 不绑定，模板跨形态误引 $source 即报错）。
 */
public final class VariantScripts {

    public static final int TITLE_MAX = 120;
    public static final int CONTENT_MAX = 32_768;

    private VariantScripts() {
    }

    @Getter
    public static class RenderResult {
        private final String title;
        private final String content;
        private final String error;
        private final List<String> console;
        private final long costMs;
        private final String engineVersion;

        public RenderResult(String title, String content, String error, List<String> console, long costMs, String engineVersion) {
            this.title = title;
            this.content = content;
            this.error = error;
            this.console = console == null ? List.of() : console;
            this.costMs = costMs;
            this.engineVersion = engineVersion;
        }
    }

    public static RenderResult run(ScriptEngine engine, String templateCode, String engineVersion,
                                   String titleScript, String contentScript, Map<String, Object> ctxBindings) {
        long start = System.currentTimeMillis();
        List<String> console = new ArrayList<>();
        String title = null;
        String content = null;
        String error = null;
        Map<String, Object> ctx = new LinkedHashMap<>(ctxBindings == null ? Map.of() : ctxBindings);
        Object source = ctx.remove("source");
        Map<String, Object> bindings = new LinkedHashMap<>();
        bindings.put("$ctx", ctx);
        if (source != null) {
            bindings.put("$source", source);
        }
        ScriptResult titleResult = engine.execute(ScriptExecution.builder()
                .content(titleScript).scriptName(templateCode + ":title")
                .profileId("alert-render")
                .engineVersion(engineVersion).bindings(bindings).build());
        console.addAll(titleResult.getConsole() == null ? List.of() : titleResult.getConsole());
        ScriptResult contentResult = engine.execute(ScriptExecution.builder()
                .content(contentScript).scriptName(templateCode + ":content")
                .profileId("alert-render")
                .engineVersion(engineVersion).bindings(bindings).build());
        console.addAll(contentResult.getConsole() == null ? List.of() : contentResult.getConsole());
        if ("SUCCESS".equals(titleResult.getStatus()) && titleResult.getValue() != null) {
            title = String.valueOf(titleResult.getValue());
        } else if (!"SUCCESS".equals(titleResult.getStatus()) && titleResult.getError() != null) {
            error = "title: " + titleResult.getError().getType() + " " + titleResult.getError().getMessage();
        }
        if ("SUCCESS".equals(contentResult.getStatus()) && contentResult.getValue() != null) {
            content = String.valueOf(contentResult.getValue());
        } else if (!"SUCCESS".equals(contentResult.getStatus()) && contentResult.getError() != null) {
            error = (error == null ? "" : error + "; ") + "content: "
                    + contentResult.getError().getType() + " " + contentResult.getError().getMessage();
        }
        if (title != null && title.length() > TITLE_MAX) {
            title = title.substring(0, TITLE_MAX);
            console.add("[warn] title 超过 120 字符已截断");
        }
        if (content != null && content.length() > CONTENT_MAX) {
            content = content.substring(0, CONTENT_MAX);
            console.add("[warn] content 超过 32KB 已截断");
        }
        return new RenderResult(title, content, error, console,
                System.currentTimeMillis() - start, contentResult.getEngineVersion());
    }
}
