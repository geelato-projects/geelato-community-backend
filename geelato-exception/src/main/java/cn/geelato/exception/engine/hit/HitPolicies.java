package cn.geelato.exception.engine.hit;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 命中策略纯函数（可离线单测）：抑制维度匹配、熔断截断、恢复冷却判定。
 */
public final class HitPolicies {

    private HitPolicies() {
    }

    /** 抑制维度匹配：BIZ=bizType+bizId、CUSTOMER=customerId、ORG=ownerOrgId；缺维度不抑制。 */
    public static boolean suppressedBy(Map<String, Object> hit, String scope, Set<String> activeKeys) {
        if (activeKeys == null || activeKeys.isEmpty()) {
            return false;
        }
        String key = switch (scope == null ? "BIZ" : scope) {
            case "CUSTOMER" -> str(hit.get("customerId"));
            case "ORG" -> str(hit.get("ownerOrgId"));
            default -> str(hit.get("bizType")) + "|" + str(hit.get("bizId"));
        };
        return key != null && !key.isBlank() && activeKeys.contains(key);
    }

    /** 熔断：超 hitLimit 截断，AGGREGATE 保留溢出量供聚合通知、SKIP 直接丢弃。 */
    public static Overflow applyHitLimit(List<Map<String, Object>> hits, Integer hitLimit) {
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

    public static String sourceKeyOf(String ruleCode, Map<String, Object> hit) {
        return ruleCode + "|" + str(hit.get("bizType")) + "|" + str(hit.get("bizId"));
    }

    public static Set<String> distinctSourceKeys(String ruleCode, List<Map<String, Object>> hits) {
        Set<String> keys = new LinkedHashSet<>();
        for (Map<String, Object> hit : hits) {
            keys.add(sourceKeyOf(ruleCode, hit));
        }
        return keys;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    public record Overflow(List<Map<String, Object>> kept, int dropped) {
    }
}
