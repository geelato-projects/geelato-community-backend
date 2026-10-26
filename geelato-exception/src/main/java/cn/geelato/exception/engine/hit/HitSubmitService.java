package cn.geelato.exception.engine.hit;

import cn.geelato.core.orm.Dao;
import cn.geelato.exception.engine.core.Hit;
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

    public SubmitReport submit(FmsExceptionRule rule, List<Hit> hits) {
        SubmitReport report = new SubmitReport();
        if (hits == null || hits.isEmpty()) {
            return report;
        }
        log.info("命中提交开始 ruleCode={} hits={}", rule.getRuleCode(), hits.size());
        // 风暴抑制配置（storm_json）：{hitLimit,overflowPolicy,cooldownMinutes,inhibitedBy}
        com.fasterxml.jackson.databind.JsonNode storm = parseStorm(rule.getStormJson());
        List<Hit> working = hits;
        // 抑制：inhibited_by 各声明的在途维度命中即抑制
        List<Hit> afterSuppress = new ArrayList<>();
        for (Hit hit : working) {
            if (isSuppressed(storm, hit)) {
                report.setSuppressedCount(report.getSuppressedCount() + 1);
                log.info("命中被抑制 ruleCode={} bizType={}|{}", rule.getRuleCode(), hit.getBizType(), hit.getBizId());
            } else {
                afterSuppress.add(hit);
            }
        }
        // 熔断
        HitPolicies.Overflow overflow = HitPolicies.applyHitLimit(afterSuppress, intOf(storm, "hitLimit"));
        working = overflow.kept();
        report.setOverflowDropped(overflow.dropped());
        if (overflow.dropped() > 0) {
            log.info("命中熔断截断 ruleCode={} kept={} dropped={}", rule.getRuleCode(), working.size(), overflow.dropped());
        }
        // 逐命中提交
        long now = System.currentTimeMillis();
        for (Hit hit : working) {
            String sourceKey = HitPolicies.sourceKeyOf(rule.getRuleCode(), hit);
            if (HitPolicies.inCooldown(lastResolvedAt(sourceKey), intOf(storm, "cooldownMinutes"), now)) {
                report.setCooldownSkipped(report.getCooldownSkipped() + 1);
                log.info("命中冷却跳过 ruleCode={} sourceKey={}", rule.getRuleCode(), sourceKey);
                continue;
            }
            ParticipantSolver.Solution solution = participantSolver.solve(rule.getOwnerSelector(), hit);
            if (solution.handlers().isEmpty()) {
                report.setNoHandlerSkipped(report.getNoHandlerSkipped() + 1);
                log.warn("命中无人认领 ruleCode={} bizId={}（选择器求解为空）", rule.getRuleCode(), hit.getBizId());
                continue;
            }
            log.info("参与者求解 ruleCode={} bizId={} handlers={} cc={}", rule.getRuleCode(), hit.getBizId(),
                    idsOf(solution.handlers()), idsOf(solution.cc()));
            String suppressKeys = suppressKeysOf(rule.getRuleCode(), hit);
            // 触达即实例：处理人与知会人（cc）都在 exception 表产生各自实例（一人一实例，工作台可见即触达）
            for (Participant participant : solution.all()) {
                try {
                    String result = lifecycle.upsertInstance(rule.getRuleCode(), rule.getRuleName(),
                            rule.getDomainCode(), rule.getLevel(), hit, participant, rule.getVersion(),
                            rule.getAppId(), suppressKeys);
                    if ("created".equals(result)) {
                        report.setInstanceCreated(report.getInstanceCreated() + 1);
                    } else {
                        report.setInstanceRenewed(report.getInstanceRenewed() + 1);
                    }
                    log.info("实例{} ruleCode={} bizId={} participant={}", "created".equals(result) ? "新建" : "续期",
                            rule.getRuleCode(), hit.getBizId(), participant.getId());
                } catch (Exception e) {
                    log.error("实例提交失败 ruleCode={} bizId={} participant={}: {}",
                            rule.getRuleCode(), hit.getBizId(), participant.getId(), e.getMessage(), e);
                }
            }
        }
        log.info("命中提交完成 ruleCode={} suppressed={} overflowDropped={} cooldownSkipped={} noHandlerSkipped={} created={} renewed={}",
                rule.getRuleCode(), report.getSuppressedCount(), report.getOverflowDropped(),
                report.getCooldownSkipped(), report.getNoHandlerSkipped(),
                report.getInstanceCreated(), report.getInstanceRenewed());
        return report;
    }

    private static String idsOf(List<Participant> participants) {
        StringBuilder sb = new StringBuilder("[");
        for (Participant p : participants) {
            if (sb.length() > 1) {
                sb.append(",");
            }
            sb.append(p.getId());
        }
        return sb.append("]").toString();
    }

    /** inhibitedBy 声明的在途维度键集合命中即抑制（单向遮蔽）：{ruleCode, keyFields?}。 */
    private boolean isSuppressed(com.fasterxml.jackson.databind.JsonNode storm, Hit hit) {
        com.fasterxml.jackson.databind.JsonNode decls =
                storm == null ? null : storm.path("inhibitedBy");
        if (decls == null || !decls.isArray()) {
            return false;
        }
        for (com.fasterxml.jackson.databind.JsonNode decl : decls) {
            String ruleCode = decl.path("ruleCode").asText(null);
            if (ruleCode == null || ruleCode.isBlank()) {
                continue;
            }
            String dimensionKey = HitPolicies.dimensionKeyOf(hit, keyFieldsOf(decl));
            if (HitPolicies.suppressedBy(dimensionKey, activeKeysOf(ruleCode))) {
                return true;
            }
        }
        return false;
    }

    /** 声明 keyFields（缺省 ["bizId"]），空白项剔除后为空同样回落缺省。 */
    private static List<String> keyFieldsOf(com.fasterxml.jackson.databind.JsonNode decl) {
        com.fasterxml.jackson.databind.JsonNode fields = decl.path("keyFields");
        if (!fields.isArray() || fields.isEmpty()) {
            return HitPolicies.DEFAULT_SUPPRESS_KEY_FIELDS;
        }
        List<String> out = new ArrayList<>();
        for (com.fasterxml.jackson.databind.JsonNode field : fields) {
            String text = field.asText(null);
            if (text != null && !text.isBlank()) {
                out.add(text.trim());
            }
        }
        return out.isEmpty() ? HitPolicies.DEFAULT_SUPPRESS_KEY_FIELDS : out;
    }

    /** 被引用规则在途实例的抑制维度键全集（suppress_keys JSON 数组展开，内存比对）。 */
    private Set<String> activeKeysOf(String ruleCode) {
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
            expandKeys(keys, row.get("suppressKeys"));
        }
        return keys;
    }

    private static void expandKeys(Set<String> keys, Object suppressKeysJson) {
        if (suppressKeysJson == null) {
            return;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode arr = MAPPER.readTree(String.valueOf(suppressKeysJson));
            if (arr.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode key : arr) {
                    if (!key.isNull() && !key.asText().isBlank()) {
                        keys.add(key.asText());
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 物化：引用本规则的各 inhibitedBy 声明 → 本命中维度键 JSON 数组（无键返回 null）。
     * 扫全量未删规则（含停用，防停用规则复启后抑制断链），规则量小不缓存。
     */
    private String suppressKeysOf(String ruleCode, Hit hit) {
        List<String> keys = new ArrayList<>();
        for (Map<String, Object> ruleRow : listRules()) {
            com.fasterxml.jackson.databind.JsonNode storm = parseStorm(str(ruleRow.get("stormJson")));
            com.fasterxml.jackson.databind.JsonNode decls =
                    storm == null ? null : storm.path("inhibitedBy");
            if (decls == null || !decls.isArray()) {
                continue;
            }
            for (com.fasterxml.jackson.databind.JsonNode decl : decls) {
                if (!ruleCode.equals(decl.path("ruleCode").asText(null))) {
                    continue;
                }
                String key = HitPolicies.dimensionKeyOf(hit, keyFieldsOf(decl));
                if (key != null && !keys.contains(key)) {
                    keys.add(key);
                }
            }
        }
        if (keys.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(keys);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<Map<String, Object>> listRules() {
        List<Map<String, Object>> rows = MetaFactory.query(FmsExceptionRule.class)
                .where(Filter.eq("delStatus", 0))
                .list();
        return rows == null ? List.of() : rows;
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
