package cn.geelato.exception.engine.core;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * hits 契约校验（三时点同一实现）：保存静态/门禁 dry-run/运行期兜底。
 * 契约：最终步返回 hits 数组，元素为对象且 bizId 必填（唯一必填项）；
 * title/snapshot/anchorTime（格式）为留痕级——缺失或非法记 violation 但不丢条；
 * bizType 脚本返回优先、规则值兜底（兜底后仍空由 PipelineEngine 记 violation）。
 */
public final class HitsContractValidator {

    /** 锚点时间兼容格式：常规 yyyy-MM-dd HH:mm:ss 与 ISO 本地时间。 */
    public static final DateTimeFormatter[] ANCHOR_TIME_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME};

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
     * 单条非对象或缺 bizId 丢弃留痕（violations），不影响其余条目；
     * 缺 title/缺 snapshot/anchorTime 格式非法为留痕级，条目保留。
     */
    public static RuntimeCheck validateRuntime(Object hitsValue) {
        if (!(hitsValue instanceof List<?> list)) {
            return new RuntimeCheck(true, List.of(), List.of(Map.of(
                    "fieldPath", "hits", "code", "HITS_NOT_ARRAY",
                    "message", "最终步骤未返回数组（hits 契约），本轮判定结果整体丢弃")));
        }
        List<Hit> valid = new ArrayList<>();
        List<Map<String, Object>> violations = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof Map<?, ?>)) {
                violations.add(issue("hits[" + i + "]", "HIT_NOT_OBJECT", "第 " + i + " 条命中不是对象，已丢弃"));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> hit = (Map<String, Object>) item;
            if (blank(hit.get("bizId"))) {
                violations.add(issue("hits[" + i + "].bizId", "REQUIRED", "第 " + i + " 条命中缺 bizId，已丢弃"));
                continue;
            }
            if (blank(hit.get("title"))) {
                violations.add(issue("hits[" + i + "].title", "TITLE_MISSING",
                        "第 " + i + " 条命中缺 title（实例标题将兜底规则名）"));
            }
            if (hit.get("snapshot") == null) {
                violations.add(issue("hits[" + i + "].snapshot", "SNAPSHOT_MISSING",
                        "第 " + i + " 条命中缺 snapshot（实例详情将无业务留证）"));
            }
            Object anchorTime = hit.get("anchorTime");
            if (anchorTime != null && !validAnchorTime(String.valueOf(anchorTime))) {
                violations.add(issue("hits[" + i + "].anchorTime", "ANCHOR_TIME_INVALID",
                        "第 " + i + " 条命中 anchorTime 不符契约格式（yyyy-MM-dd HH:mm:ss 或 ISO 本地时间）: "
                                + anchorTime));
            }
            valid.add(Hit.fromMap(hit));
        }
        return new RuntimeCheck(false, valid, violations);
    }

    /** anchorTime 可否解析（null/空视为未提供，合法）。 */
    public static boolean validAnchorTime(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        for (DateTimeFormatter f : ANCHOR_TIME_FORMATS) {
            try {
                LocalDateTime.parse(text, f);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
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

    public record RuntimeCheck(boolean fatal, List<Hit> validHits, List<Map<String, Object>> violations) {
    }
}
