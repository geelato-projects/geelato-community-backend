package cn.geelato.exception.engine.hit;

import cn.geelato.exception.engine.core.Hit;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 命中策略纯函数（可离线单测）：抑制维度键拼装与匹配、熔断截断、恢复冷却判定。
 */
public final class HitPolicies {

    private HitPolicies() {
    }

    /** 抑制维度缺省键字段：同一业务对象跨规则遮蔽。 */
    public static final List<String> DEFAULT_SUPPRESS_KEY_FIELDS = List.of("bizId");

    /**
     * 维度键拼装：keyFields（缺省 ["bizId"]）逐字段从 hit 取值（契约字段或 extras），
     * 拼 "field1|v1|field2|v2"（字段名参与防混撞）；任一字段缺值返回 null（缺维度不抑制）。
     */
    public static String dimensionKeyOf(Hit hit, List<String> keyFields) {
        List<String> fields = keyFields == null || keyFields.isEmpty() ? DEFAULT_SUPPRESS_KEY_FIELDS : keyFields;
        StringBuilder sb = new StringBuilder();
        for (String field : fields) {
            Object value = hit.getField(field);
            if (value == null || String.valueOf(value).isBlank()) {
                return null;
            }
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(field).append('|').append(value);
        }
        return sb.toString();
    }

    /** 抑制判定：维度键命中被引用规则的在途键集合。 */
    public static boolean suppressedBy(String dimensionKey, Set<String> activeKeys) {
        return dimensionKey != null && activeKeys != null && !activeKeys.isEmpty()
                && activeKeys.contains(dimensionKey);
    }

    /** 熔断：超 hitLimit 截断，AGGREGATE 保留溢出量供聚合通知、SKIP 直接丢弃。 */
    public static Overflow applyHitLimit(List<Hit> hits, Integer hitLimit) {
        if (hitLimit == null || hitLimit <= 0 || hits.size() <= hitLimit) {
            return new Overflow(hits, 0);
        }
        return new Overflow(new ArrayList<>(hits.subList(0, hitLimit)), hits.size() - hitLimit);
    }

    /** 恢复冷却：同 source_key 存在终态实例且 resolved_at 距今不足冷却分钟 → 跳过重建（防抖动）。 */
    public static boolean inCooldown(Long resolvedAtEpochMs, Integer cooldownMinutes, long nowEpochMs) {
        if (resolvedAtEpochMs == null || cooldownMinutes == null || cooldownMinutes <= 0) {
            return false;
        }
        return nowEpochMs - resolvedAtEpochMs < cooldownMinutes * 60_000L;
    }

    public static String sourceKeyOf(String ruleCode, Hit hit) {
        return ruleCode + "|" + hit.getBizType() + "|" + hit.getBizId();
    }

    public static String sourceKeyOf(String ruleCode, String bizType, String bizId) {
        return ruleCode + "|" + bizType + "|" + bizId;
    }

    public static Set<String> distinctSourceKeys(String ruleCode, List<Hit> hits) {
        Set<String> keys = new LinkedHashSet<>();
        for (Hit hit : hits) {
            keys.add(sourceKeyOf(ruleCode, hit));
        }
        return keys;
    }

    public record Overflow(List<Hit> kept, int dropped) {
    }
}
