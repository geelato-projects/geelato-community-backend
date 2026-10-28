package cn.geelato.alert.engine.silence;

import cn.geelato.alert.entity.FmsAlertSilence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Date;

/**
 * 静默判定（引擎与试匹配共用）：窗口（ONCE 绝对窗 / RECURRING 周期窗分钟粗判）
 * + 三维交集（alertCodes/ruleCodes/customerIds 声明则须包含，未声明不设限）。
 * RESOLVE 通知被 allowResolve=1 的静默豁免（P-A5：恢复通知缺省放行）。
 */
public final class SilenceChecker {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SilenceChecker() {
    }

    /**
     * 投递前终判：命中返回静默 id（SILENCED 落 silence_id），未命中返回 null。
     *
     * @param triggerType 记录触发类型（RESOLVE 时按 allowResolve 豁免）
     * @param customerId  命中源实例首个客户维度（可空）
     */
    public static String check(Iterable<FmsAlertSilence> actives, String alertCode, String ruleCode,
                               String customerId, String triggerType, Date now) {
        boolean resolve = "RESOLVE".equals(triggerType);
        for (FmsAlertSilence s : actives) {
            if (!withinWindow(s, now)) {
                continue;
            }
            if (resolve && s.getAllowResolve() == 1) {
                continue;
            }
            if (hit(s.getMatchJson(), alertCode, ruleCode, customerId)) {
                return s.getId();
            }
        }
        return null;
    }

    /** 声明了维度则须全部命中（未声明=全量静默）。 */
    public static boolean hit(String matchJson, String alertCode, String ruleCode, String customerId) {
        JsonNode match = parseDocumentOrNull(matchJson);
        if (match == null) {
            return true;
        }
        boolean declared = match.has("alertCodes") || match.has("ruleCodes") || match.has("customerIds");
        return !declared
                || (dimensionDeclaredHit(match.path("alertCodes"), alertCode)
                && dimensionDeclaredHit(match.path("ruleCodes"), ruleCode)
                && dimensionDeclaredHit(match.path("customerIds"), customerId));
    }

    /** ONCE：绝对窗；RECURRING：总区间 + 周期窗（days 0=周日..6=周六 + 每日时段，分钟精度粗判）。 */
    public static boolean withinWindow(FmsAlertSilence s, Date now) {
        if (s.getStartAt() == null || s.getEndAt() == null) {
            return false;
        }
        if (now.before(s.getStartAt()) || now.after(s.getEndAt())) {
            return false;
        }
        if (!"RECURRING".equals(s.getWindowType())) {
            return true;
        }
        JsonNode tw = parseDocumentOrNull(s.getTimeWindow());
        if (tw == null) {
            return true;
        }
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(now);
        JsonNode days = tw.path("days");
        if (days.isArray() && !days.isEmpty()) {
            // 0=周日..6=周六（JS 惯例）；Calendar DAY_OF_WEEK 周日=1..周六=7，直接减一换算
            int dow = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1;
            boolean dayHit = false;
            for (JsonNode d : days) {
                if (d.asInt(-1) == dow) {
                    dayHit = true;
                    break;
                }
            }
            if (!dayHit) {
                return false;
            }
        }
        int minutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);
        int start = parseHHmm(tw.path("startTime").asText(null), 0);
        int end = parseHHmm(tw.path("endTime").asText(null), 24 * 60);
        return minutes >= start && minutes < end;
    }

    /** 声明了该维度时是否真实命中（未声明恒真）。 */
    public static boolean dimensionDeclaredHit(JsonNode arr, String value) {
        if (arr == null || arr.isMissingNode() || arr.isNull() || !arr.isArray() || arr.isEmpty()) {
            return true;
        }
        return value != null && !value.isBlank() && containsText(arr, value.trim());
    }

    private static int parseHHmm(String text, int def) {
        if (text == null || !text.contains(":")) {
            return def;
        }
        String[] parts = text.split(":");
        try {
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean containsText(JsonNode arr, String value) {
        for (JsonNode n : arr) {
            if (value.equals(n.asText())) {
                return true;
            }
        }
        return false;
    }

    private static JsonNode parseDocumentOrNull(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
