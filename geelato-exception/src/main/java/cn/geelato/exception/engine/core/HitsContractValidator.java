package cn.geelato.exception.engine.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * hits 契约校验（三时点同一实现）：保存静态/门禁 dry-run/运行期兜底。
 * 契约：最终步返回 hits 数组，元素 bizType/bizId 必填，levelOverride 值域 P0~P3。
 */
public final class HitsContractValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> LEVELS = Set.of("P0", "P1", "P2", "P3");

    private HitsContractValidator() {
    }

    /** 静态出口启发式：最终步 js 含 return / python 含 __result__（不执行脚本）。 */
    public static List<Map<String, Object>> checkStepExit(JsonNode steps) {
        List<Map<String, Object>> issues = new ArrayList<>();
        if (steps == null || !steps.isArray() || steps.isEmpty()) {
            return issues;
        }
        JsonNode last = steps.get(steps.size() - 1);
        String lang = text(last, "lang");
        String content = text(last, "content");
        if (content == null || content.isBlank()) {
            return issues;
        }
        boolean jsExit = (lang == null || "js".equals(lang)) && content.contains("return");
        boolean pyExit = "python".equals(lang) && content.contains("__result__");
        if (!jsExit && !pyExit) {
            issues.add(issue("spec.pipeline.steps[last].content", "HITS_EXIT_MISSING",
                    "无法确认 hits 契约：最终步骤未见 return（js）或 __result__（python）出口"));
        }
        return issues;
    }

    /**
     * 运行期兜底：最终步返回值复验。fatal=true 表示整体非数组（本轮跳过）；
     * 单条缺必填/值域非法丢弃留痕（violations），不影响其余条目。
     */
    public static RuntimeCheck validateRuntime(Object hitsValue) {
        if (!(hitsValue instanceof List<?> list)) {
            return new RuntimeCheck(true, List.of(), List.of(Map.of(
                    "fieldPath", "hits", "code", "HITS_NOT_ARRAY",
                    "message", "最终步骤未返回数组（hits 契约），本轮判定结果整体丢弃")));
        }
        List<Map<String, Object>> valid = new ArrayList<>();
        List<Map<String, Object>> violations = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof Map<?, ?>)) {
                violations.add(issue("hits[" + i + "]", "HIT_NOT_OBJECT", "第 " + i + " 条命中不是对象，已丢弃"));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> hit = (Map<String, Object>) item;
            if (blank(hit.get("bizType"))) {
                violations.add(issue("hits[" + i + "].bizType", "REQUIRED", "第 " + i + " 条命中缺 bizType，已丢弃"));
                continue;
            }
            if (blank(hit.get("bizId"))) {
                violations.add(issue("hits[" + i + "].bizId", "REQUIRED", "第 " + i + " 条命中缺 bizId，已丢弃"));
                continue;
            }
            Object level = hit.get("levelOverride");
            if (level != null && !LEVELS.contains(String.valueOf(level))) {
                violations.add(issue("hits[" + i + "].levelOverride", "VALUE_INVALID",
                        "第 " + i + " 条命中 levelOverride 值域非法（" + level + "，允许 P0~P3），已丢弃"));
                continue;
            }
            valid.add(hit);
        }
        return new RuntimeCheck(false, valid, violations);
    }

    private static boolean blank(Object v) {
        return v == null || String.valueOf(v).isBlank();
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static Map<String, Object> issue(String fieldPath, String code, String message) {
        return Map.of("fieldPath", fieldPath, "code", code, "message", message);
    }

    public record RuntimeCheck(boolean fatal, List<Map<String, Object>> validHits,
                               List<Map<String, Object>> violations) {
    }
}
