package cn.geelato.exception.engine.hit;

import cn.geelato.core.orm.Dao;
import cn.geelato.exception.engine.lifecycle.InstanceLifecycleService;
import cn.geelato.exception.engine.participant.Participant;
import cn.geelato.exception.engine.participant.ParticipantSolver;
import cn.geelato.exception.entity.FmsException;
import cn.geelato.exception.entity.FmsExceptionRule;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.query.Filter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 命中提交链：契约兜定后的有效 hits → 抑制（inhibited_by）→ 熔断（hit_limit）→
 * 冷却（cooldown）→ 参与者求解 → 一人一实例 upsert + 知会触达。
 */
@Slf4j
public class HitSubmitService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Dao dynamicDao;
    private final ParticipantSolver participantSolver;
    private final InstanceLifecycleService lifecycle;

    public HitSubmitService(@Qualifier("dynamicDao") Dao dynamicDao,
                            ParticipantSolver participantSolver,
                            InstanceLifecycleService lifecycle) {
        this.dynamicDao = dynamicDao;
        this.participantSolver = participantSolver;
        this.lifecycle = lifecycle;
    }

    public SubmitReport submit(FmsExceptionRule rule, List<Map<String, Object>> hits) {
        SubmitReport report = new SubmitReport();
        if (hits == null || hits.isEmpty()) {
            return report;
        }
        // 风暴抑制配置（storm_json）：{hitLimit,overflowPolicy,cooldownMinutes,inhibitedBy}
        com.fasterxml.jackson.databind.JsonNode storm = parseStorm(rule.getStormJson());
        List<Map<String, Object>> working = hits;
        // 抑制：inhibited_by 各声明的在途维度命中即抑制
        List<Map<String, Object>> afterSuppress = new ArrayList<>();
        for (Map<String, Object> hit : working) {
            if (isSuppressed(storm, hit)) {
                report.setSuppressedCount(report.getSuppressedCount() + 1);
            } else {
                afterSuppress.add(hit);
            }
        }
        // 熔断
        HitPolicies.Overflow overflow = HitPolicies.applyHitLimit(afterSuppress, intOf(storm, "hitLimit"));
        working = overflow.kept();
        report.setOverflowDropped(overflow.dropped());
        // 逐命中提交
        long now = System.currentTimeMillis();
        for (Map<String, Object> hit : working) {
            String sourceKey = HitPolicies.sourceKeyOf(rule.getRuleCode(), hit);
            if (HitPolicies.inCooldown(lastResolvedAt(sourceKey), intOf(storm, "cooldownMinutes"), now)) {
                report.setCooldownSkipped(report.getCooldownSkipped() + 1);
                continue;
            }
            ParticipantSolver.Solution solution = participantSolver.solve(rule.getOwnerSelector(), hit);
            if (solution.handlers().isEmpty()) {
                report.setNoHandlerSkipped(report.getNoHandlerSkipped() + 1);
                log.warn("命中无人认领 ruleCode={} bizId={}（选择器求解为空）", rule.getRuleCode(), hit.get("bizId"));
                continue;
            }
            // 触达即实例：处理人与知会人（cc）都在 exception 表产生各自实例（一人一实例，工作台可见即触达）
            for (Participant participant : solution.all()) {
                try {
                    String result = lifecycle.upsertInstance(rule.getRuleCode(), rule.getRuleName(),
                            rule.getDomainCode(), rule.getLevel(), hit, participant, rule.getVersion(), rule.getAppId());
                    if ("created".equals(result)) {
                        report.setInstanceCreated(report.getInstanceCreated() + 1);
                    } else {
                        report.setInstanceRenewed(report.getInstanceRenewed() + 1);
                    }
                } catch (Exception e) {
                    log.error("实例提交失败 ruleCode={} bizId={} participant={}: {}",
                            rule.getRuleCode(), hit.get("bizId"), participant.getId(), e.getMessage(), e);
                }
            }
        }
        return report;
    }

    /** inhibited_by 声明规则的在途实例维度集合命中即抑制（单向遮蔽）。 */
    private boolean isSuppressed(com.fasterxml.jackson.databind.JsonNode storm, Map<String, Object> hit) {
        com.fasterxml.jackson.databind.JsonNode decls =
                storm == null ? null : storm.path("inhibitedBy");
        if (decls == null || !decls.isArray()) {
            return false;
        }
        for (com.fasterxml.jackson.databind.JsonNode decl : decls) {
            String ruleCode = decl.path("ruleCode").asText(null);
            String scope = decl.path("scope").asText(null);
            if (ruleCode == null || ruleCode.isBlank()) {
                continue;
            }
            Set<String> activeKeys = activeKeysOf(ruleCode, scope);
            if (HitPolicies.suppressedBy(hit, scope, activeKeys)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> activeKeysOf(String ruleCode, String scope) {
        List<Map<String, Object>> rows = MetaFactory.query(FmsException.class)
                .where(Filter.eq("ruleCode", ruleCode),
                        Filter.in("status", List.of("OPEN", "PROCESSING")),
                        Filter.eq("delStatus", 0))
                .list();
        Set<String> keys = new HashSet<>();
        if (rows == null) {
            return keys;
        }
        for (Map<String, Object> row : rows) {
            switch (scope == null ? "BIZ" : scope) {
                case "CUSTOMER" -> addKey(keys, row.get("customerId"));
                case "ORG" -> addKey(keys, row.get("participantOrg"));
                default -> addKey(keys, row.get("bizType") + "|" + row.get("bizId"));
            }
        }
        return keys;
    }

    private static void addKey(Set<String> keys, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            keys.add(String.valueOf(value));
        }
    }

    /** 同源组最近终态时刻（恢复冷却判定输入），无终态返回 null。 */
    private Long lastResolvedAt(String sourceKey) {
        List<Map<String, Object>> rows = MetaFactory.query(FmsException.class)
                .where(Filter.eq("sourceKey", sourceKey),
                        Filter.eq("delStatus", 0))
                .list();
        Long latest = null;
        if (rows == null) {
            return null;
        }
        for (Map<String, Object> row : rows) {
            Object resolvedAt = row.get("resolvedAt");
            Date date = toDate(resolvedAt);
            if (date != null && (latest == null || date.getTime() > latest)) {
                latest = date.getTime();
            }
        }
        return latest;
    }

    /** storm_json 解析（空/坏 JSON 返回 null，各抑制策略自然跳过）。 */
    private static com.fasterxml.jackson.databind.JsonNode parseStorm(String stormJson) {
        if (stormJson == null || stormJson.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(stormJson);
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer intOf(com.fasterxml.jackson.databind.JsonNode storm, String field) {
        com.fasterxml.jackson.databind.JsonNode n = storm == null ? null : storm.path(field);
        return n == null || n.isMissingNode() || n.isNull() ? null : n.asInt();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Date toDate(Object o) {
        if (o instanceof Date d) {
            return d;
        }
        if (o instanceof java.time.LocalDateTime ldt) {
            return java.util.Date.from(ldt.atZone(java.time.ZoneId.systemDefault()).toInstant());
        }
        return null;
    }
}
