package cn.geelato.alert.engine.receiver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 接收人求解编排：receiver_json {@code {extra[]}} 按选择器类型分发，并集去重。
 * 全空时兜底 fallbackRoleCode 角色（未配置则返回空，调用方记录 FAIL 硬失败）。
 */
@Slf4j
public class AlertReceiverSolver {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, ReceiverSelectorResolver> resolvers = new LinkedHashMap<>();
    private final cn.geelato.security.SecurityProvider securityProvider;
    private final String fallbackRoleCode;

    public AlertReceiverSolver(List<ReceiverSelectorResolver> resolverList,
                               cn.geelato.security.SecurityProvider securityProvider, String fallbackRoleCode) {
        for (ReceiverSelectorResolver resolver : resolverList) {
            resolvers.put(resolver.type(), resolver);
        }
        this.securityProvider = securityProvider;
        this.fallbackRoleCode = fallbackRoleCode == null || fallbackRoleCode.isBlank() ? null : fallbackRoleCode.trim();
    }

    /**
     * @param receiverJson 规则 receiver_json 列原文（可空=无接收人）
     */
    public List<AlertReceiver> solve(String receiverJson, AlertResolveCtx ctx) {
        JsonNode root = parse(receiverJson);
        Map<String, AlertReceiver> merged = new LinkedHashMap<>();
        JsonNode extra = root == null ? null : root.path("extra");
        if (extra != null && extra.isArray()) {
            for (JsonNode item : extra) {
                if (!item.isObject()) {
                    continue;
                }
                Map<String, Object> selector = MAPPER.convertValue(item, Map.class);
                String type = selector.get("type") == null ? null : String.valueOf(selector.get("type"));
                ReceiverSelectorResolver resolver = resolvers.get(type);
                if (resolver == null) {
                    log.warn("未知接收人选择器类型: {}（跳过）", type);
                    continue;
                }
                try {
                    for (AlertReceiver r : resolver.resolve(selector, ctx)) {
                        addMerged(merged, r);
                    }
                } catch (Exception e) {
                    log.warn("接收人求解失败 type={} selector={}: {}", type, selector, e.getMessage());
                }
            }
        }
        if (merged.isEmpty() && fallbackRoleCode != null && securityProvider != null) {
            for (cn.geelato.security.User user : securityProvider.queryUsersOfRole(fallbackRoleCode)) {
                AlertReceiver r = ReceiverSelectors.of(user);
                if (r != null) {
                    addMerged(merged, r);
                }
            }
            if (!merged.isEmpty()) {
                log.warn("接收人求解为空，已兜底角色 {} 共 {} 人", fallbackRoleCode, merged.size());
            }
        }
        return new ArrayList<>(merged.values());
    }

    private static void addMerged(Map<String, AlertReceiver> merged, AlertReceiver r) {
        if (r == null || r.getId() == null || r.getId().isBlank()) {
            return;
        }
        merged.putIfAbsent(r.getId(), r);
    }

    private static JsonNode parse(String json) {
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
