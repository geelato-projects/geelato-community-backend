package cn.geelato.alert.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 源业务数据快照（platform_alert.source_json）的采集与解析：
 * 告警产生/合并/恢复/重复提醒时由摄入侧（scanner/grouper/resolver）采集源表行写入，
 * 渲染/冲刷/投递只读告警记录自身，不回查源表（告警与源模块以告警落库时刻的数据解耦）。
 *
 * <p>结构按形态：single（EVENT×INDEPENDENT / RESOLVE）= 源实例全量对象；
 * group（AGGREGATE / REPEAT）= 清单数组（未截断，渲染时截断与统计）；SCHEDULE 不落（dataset 在 stats_json）。
 */
public final class SourceSnapshots {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 源实例全量列（single 形态采集，含 detail_json 业务快照）。 */
    public static final String SINGLE_COLUMNS =
            "id, rule_code, rule_name, domain_code, level, status, current_step_code, escalation_level, "
                    + "biz_type, biz_id, biz_label, participant_id, participant_name, "
                    + "participant_org, first_at, last_seen_at, hit_count, anchor_elapsed, title, detail_json, "
                    + "resolved_at, resolved_type";

    /** 清单精简列（group 形态采集）。 */
    public static final String GROUP_COLUMNS =
            "id, rule_code, rule_name, domain_code, level, status, biz_label, participant_name, "
                    + "first_at, anchor_elapsed, title, detail_json";

    private SourceSnapshots() {
    }

    // ==================== 采集：源表行 → source_json ====================

    /** 源实例全量行 → single 对象 JSON（$ctx.source 结构：全字段 camelCase + detail 展开）。 */
    public static String writeSingle(Map<String, Object> row) {
        try {
            return MAPPER.writeValueAsString(singleOf(row));
        } catch (Exception e) {
            throw new IllegalStateException("source_json 序列化失败: " + e.getMessage(), e);
        }
    }

    /** 清单行列表 → group 数组 JSON。 */
    public static String writeGroupAll(List<Map<String, Object>> rows) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            items.add(groupItemOf(row));
        }
        try {
            return MAPPER.writeValueAsString(items);
        } catch (Exception e) {
            throw new IllegalStateException("source_json 序列化失败: " + e.getMessage(), e);
        }
    }

    /** 组合并：既有数组按 id 去重追加新行（重复命中保留既有快照，不改写）。 */
    public static String mergeGroup(String existingJson, Map<String, Object> row) {
        List<Map<String, Object>> items = parseItems(existingJson);
        String id = str(pick(row, "id"));
        boolean exists = items.stream().anyMatch(i -> id != null && id.equals(str(i.get("id"))));
        if (!exists) {
            items.add(groupItemOf(row));
        }
        try {
            return MAPPER.writeValueAsString(items);
        } catch (Exception e) {
            throw new IllegalStateException("source_json 序列化失败: " + e.getMessage(), e);
        }
    }

    /** 源实例行 → $ctx.source 全量对象（全字段 camelCase + detail{title,snapshot} 展开）。 */
    public static Map<String, Object> singleOf(Map<String, Object> row) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("id", pick(row, "id"));
        e.put("ruleCode", pick(row, "rule_code", "ruleCode"));
        e.put("ruleName", pick(row, "rule_name", "ruleName"));
        e.put("domainCode", pick(row, "domain_code", "domainCode"));
        e.put("level", pick(row, "level"));
        e.put("status", pick(row, "status"));
        e.put("currentStepCode", pick(row, "current_step_code", "currentStepCode"));
        e.put("escalationLevel", pick(row, "escalation_level", "escalationLevel"));
        e.put("bizType", pick(row, "biz_type", "bizType"));
        e.put("bizId", pick(row, "biz_id", "bizId"));
        e.put("bizLabel", pick(row, "biz_label", "bizLabel"));
        e.put("participantId", pick(row, "participant_id", "participantId"));
        e.put("participantName", pick(row, "participant_name", "participantName"));
        e.put("participantOrg", pick(row, "participant_org", "participantOrg"));
        e.put("firstAt", fmtTime(pick(row, "first_at", "firstAt")));
        e.put("lastSeenAt", fmtTime(pick(row, "last_seen_at", "lastSeenAt")));
        e.put("hitCount", pick(row, "hit_count", "hitCount"));
        e.put("anchorElapsed", pick(row, "anchor_elapsed", "anchorElapsed"));
        e.put("title", pick(row, "title"));
        e.put("detail", parseDetail(pick(row, "detail_json", "detailJson")));
        e.put("resolvedAt", fmtTime(pick(row, "resolved_at", "resolvedAt")));
        e.put("resolvedType", pick(row, "resolved_type", "resolvedType"));
        return e;
    }

    /** 清单行 → $ctx.alerts 元素（精简列 + detail 摘要 currency/amountLocal，取自 detail_json.snapshot）。 */
    public static Map<String, Object> groupItemOf(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", pick(row, "id"));
        item.put("ruleCode", pick(row, "rule_code", "ruleCode"));
        item.put("ruleName", pick(row, "rule_name", "ruleName"));
        item.put("domainCode", pick(row, "domain_code", "domainCode"));
        item.put("level", pick(row, "level"));
        item.put("status", pick(row, "status"));
        item.put("bizLabel", pick(row, "biz_label", "bizLabel"));
        item.put("participantName", pick(row, "participant_name", "participantName"));
        item.put("firstAt", fmtTime(pick(row, "first_at", "firstAt")));
        item.put("anchorElapsed", pick(row, "anchor_elapsed", "anchorElapsed"));
        item.put("title", pick(row, "title"));
        Map<String, Object> brief = new LinkedHashMap<>();
        Map<String, Object> snapshot = snapshotOf(parseDetail(pick(row, "detail_json", "detailJson")));
        if (snapshot != null) {
            brief.put("currency", snapshot.get("currency"));
            brief.put("amountLocal", snapshot.get("amountLocal"));
        }
        item.put("detail", brief);
        return item;
    }

    // ==================== 解析：source_json → 消费结构 ====================

    /** 首条源实例（single=对象自身；group=数组首条；空数组/空值返回 null）。坏 JSON/结构不符抛异常。 */
    public static Map<String, Object> parseItem(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        JsonNode node = readTree(json);
        if (node.isObject()) {
            return MAPPER.convertValue(node, Map.class);
        }
        if (node.isArray()) {
            return node.isEmpty() ? null : MAPPER.convertValue(node.get(0), Map.class);
        }
        throw new IllegalStateException("source_json 结构不符（应为对象或数组）");
    }

    /** 清单数组（group 消费）。空值返回空表；非数组结构抛异常。 */
    public static List<Map<String, Object>> parseItems(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        JsonNode node = readTree(json);
        if (!node.isArray()) {
            throw new IllegalStateException("source_json 结构不符（应为清单数组）");
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode n : node) {
            if (!n.isObject()) {
                throw new IllegalStateException("source_json 清单元素应为对象");
            }
            items.add(MAPPER.convertValue(n, Map.class));
        }
        return items;
    }

    /** 清单行金额（item.detail.amountLocal；缺省/不可解析返回 null）。 */
    public static Double amountOf(Map<String, Object> item) {
        Object detail = item == null ? null : item.get("detail");
        if (!(detail instanceof Map<?, ?> m)) {
            return null;
        }
        Object v = m.get("amountLocal");
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return v == null ? null : Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ==================== 内部辅助 ====================

    private static JsonNode readTree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("source_json 解析失败: " + e.getMessage(), e);
        }
    }

    /** detail_json（{title,snapshot}）→ Map；非对象/解析失败返回 null。 */
    private static Map<String, Object> parseDetail(Object detailJson) {
        if (detailJson == null) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(String.valueOf(detailJson));
            return node.isObject() ? MAPPER.convertValue(node, Map.class) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, Object> snapshotOf(Map<String, Object> detail) {
        if (detail != null && detail.get("snapshot") instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
            return out;
        }
        return null;
    }

    public static String fmtTime(Object o) {
        java.util.Date d = toDate(o);
        return d == null ? null : new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    public static java.util.Date toDate(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof java.util.Date d) {
            return d;
        }
        if (o instanceof java.time.LocalDateTime ldt) {
            return java.util.Date.from(ldt.atZone(java.time.ZoneId.systemDefault()).toInstant());
        }
        if (o instanceof java.time.Instant instant) {
            return java.util.Date.from(instant);
        }
        return null;
    }

    public static Object pick(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        return null;
    }

    public static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
