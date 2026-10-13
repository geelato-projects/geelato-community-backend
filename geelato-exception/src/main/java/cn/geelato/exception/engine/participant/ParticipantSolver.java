package cn.geelato.exception.engine.participant;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 参与者求解编排：owner_selector {handlers:[],cc:[]} 按类型分发、按用户去重。
 * 全部 handler 求解为空时返回空（调用方兜底告警管理员或仅留痕）。
 */
@Slf4j
public class ParticipantSolver {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, ParticipantResolver> resolvers = new LinkedHashMap<>();

    public ParticipantSolver(List<ParticipantResolver> resolverList) {
        for (ParticipantResolver resolver : resolverList) {
            resolvers.put(resolver.type(), resolver);
        }
    }

    public Solution solve(String ownerSelectorJson, Map<String, Object> hit) {
        List<Participant> handlers = new ArrayList<>();
        List<Participant> cc = new ArrayList<>();
        Map<String, Object> root = parse(ownerSelectorJson);
        for (Participant p : resolveList(root == null ? null : root.get("handlers"), hit, "handlers")) {
            handlers.add(p);
        }
        for (Participant p : resolveList(root == null ? null : root.get("cc"), hit, "cc")) {
            cc.add(p);
        }
        dedup(handlers);
        dedup(cc);
        return new Solution(handlers, cc);
    }

    private List<Participant> resolveList(Object selectors, Map<String, Object> hit, String group) {
        List<Participant> out = new ArrayList<>();
        if (!(selectors instanceof List<?> list)) {
            return out;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> selector = (Map<String, Object>) item;
            String type = selector.get("type") == null ? null : String.valueOf(selector.get("type"));
            ParticipantResolver resolver = resolvers.get(type);
            if (resolver == null) {
                log.warn("未知参与者选择器类型: {}（跳过）", type);
                continue;
            }
            try {
                out.addAll(resolver.resolve(selector, hit));
            } catch (Exception e) {
                log.warn("参与者求解失败 type={} selector={}: {}", type, selector, e.getMessage());
            }
        }
        return out;
    }

    private static void dedup(List<Participant> participants) {
        Set<String> seen = new LinkedHashSet<>();
        participants.removeIf(p -> p.getId() == null || p.getId().isBlank() || !seen.add(p.getId()));
    }

    private static Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    public record Solution(List<Participant> handlers, List<Participant> cc) {
        /** 处理人 + 知会人（触达即实例，全员各建一条）。 */
        public List<Participant> all() {
            List<Participant> all = new ArrayList<>(handlers);
            all.addAll(cc);
            return all;
        }
    }
}
