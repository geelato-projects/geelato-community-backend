package cn.geelato.exception.engine.trigger;

import cn.geelato.core.orm.Dao;
import cn.geelato.exception.engine.core.PipelineEngine;
import cn.geelato.exception.engine.core.PipelineOutcome;
import cn.geelato.exception.engine.core.TriggerMode;
import cn.geelato.exception.engine.hit.HitPolicies;
import cn.geelato.exception.engine.hit.HitSubmitService;
import cn.geelato.exception.engine.lifecycle.InstanceLifecycleService;
import cn.geelato.exception.engine.scan.ScanRunner;
import cn.geelato.exception.engine.support.EngineContextExecutor;
import cn.geelato.exception.entity.FmsExceptionRule;
import cn.geelato.exception.entity.FmsExceptionScanLog;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.query.Filter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 变更事件复评：listen_entities 匹配（entity + operation + PRIMARY/RELATED）→
 * RELATED 归一化（refField 白名单校验，子表 id 换主单 id）→ RECHECK 单对象复评
 * （$ctx.bizId 注入，与扫描同管道口径不漂移）→ 命中提交 / 不命中且在途组级恢复。
 * 事件只影响时效不影响正确性：重复/乱序/幽灵信号无害，判定以复评时库内现查数据为准，扫描兜底。
 */
@Slf4j
public class EntityEventReactor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_]+");

    private final Dao dynamicDao;
    private final PipelineEngine pipelineEngine;
    private final HitSubmitService hitSubmitService;
    private final InstanceLifecycleService lifecycle;

    public EntityEventReactor(@Qualifier("dynamicDao") Dao dynamicDao,
                              PipelineEngine pipelineEngine,
                              HitSubmitService hitSubmitService,
                              InstanceLifecycleService lifecycle) {
        this.dynamicDao = dynamicDao;
        this.pipelineEngine = pipelineEngine;
        this.hitSubmitService = hitSubmitService;
        this.lifecycle = lifecycle;
    }

    public void onEvent(String entityName, String operation, String bizId, String tenantCode, String eventId) {
        List<FmsExceptionRule> matched = matchRules(entityName, operation, tenantCode);
        if (matched.isEmpty()) {
            return;
        }
        for (FmsExceptionRule rule : matched) {
            try {
                recheck(rule, entityName, operation, bizId, eventId);
            } catch (Exception e) {
                log.warn("事件复评失败 ruleCode={} eventId={}: {}", rule.getRuleCode(), eventId, e.getMessage());
            }
        }
    }

    private void recheck(FmsExceptionRule rule, String entityName, String operation,
                         String bizId, String eventId) {
        EngineContextExecutor.withContext(rule.getTenantCode(), () -> {
            ListenEntry entry = matchEntry(rule, entityName, operation);
            if (entry == null) {
                return null;
            }
            String normalizedBizId = "RELATED".equals(entry.role) ? normalize(entry, bizId) : bizId;
            if (normalizedBizId == null || normalizedBizId.isBlank()) {
                return null;
            }
            Date startAt = new Date();
            PipelineOutcome outcome = pipelineEngine.execute(rule, TriggerMode.RECHECK, normalizedBizId);
            int recovered = 0;
            if ("SUCCESS".equals(outcome.getStatus())) {
                if (!outcome.getValidHits().isEmpty()) {
                    hitSubmitService.submit(rule, outcome.getValidHits());
                } else {
                    String sourceKey = HitPolicies.sourceKeyOf(rule.getRuleCode(), rule.getBizType(), normalizedBizId);
                    recovered = lifecycle.recoverSourceGroup(rule.getRuleCode(), sourceKey, "变更复评不命中");
                }
            }
            writeRecheckLog(rule, startAt, outcome, recovered,
                    Map.of("eventId", nz(eventId), "entityName", nz(entityName), "operation", nz(operation),
                            "bizId", nz(bizId), "normalizedBizId", nz(normalizedBizId)));
            return null;
        });
    }

    private List<FmsExceptionRule> matchRules(String entityName, String operation, String tenantCode) {
        List<Map<String, Object>> rows = EngineContextExecutor.withSystemContext(() ->
                MetaFactory.query(FmsExceptionRule.class)
                        .where(Filter.eq("enableStatus", 1),
                                Filter.in("triggerType", List.of("CHANGE", "BOTH")),
                                Filter.eq("tenantCode", tenantCode == null || tenantCode.isBlank() ? "geelato" : tenantCode),
                                Filter.eq("delStatus", 0))
                        .list());
        List<FmsExceptionRule> matched = new ArrayList<>();
        if (rows == null) {
            return matched;
        }
        for (Map<String, Object> row : rows) {
            FmsExceptionRule rule = toRule(row);
            if (matchEntry(rule, entityName, operation) != null) {
                matched.add(rule);
            }
        }
        return matched;
    }

    private ListenEntry matchEntry(FmsExceptionRule rule, String entityName, String operation) {
        for (ListenEntry entry : parseListen(rule.getListenEntities())) {
            if (entityName.equals(entry.entity)
                    && (entry.operations == null || entry.operations.isEmpty() || entry.operations.contains(operation))) {
                return entry;
            }
        }
        return null;
    }

    /** RELATED 归一化：SELECT {refField} FROM {entity} WHERE id = ?（表/列名来自 listen 声明并作标识符白名单校验）。 */
    private String normalize(ListenEntry entry, String bizId) {
        String refField = entry.refField == null ? "id" : entry.refField;
        if (!IDENTIFIER.matcher(entry.entity).matches() || !IDENTIFIER.matcher(refField).matches()) {
            log.warn("RELATED 归一化拒绝非标识符 entity={} refField={}", entry.entity, refField);
            return null;
        }
        List<Map<String, Object>> rows = dynamicDao.nativeQueryForMapList(
                "SELECT " + refField + " FROM " + entry.entity + " WHERE id = ?", new Object[]{bizId});
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        Object value = rows.get(0).values().iterator().next();
        return value == null ? null : String.valueOf(value);
    }

    private void writeRecheckLog(FmsExceptionRule rule, Date startAt, PipelineOutcome outcome,
                                 int recovered, Map<String, Object> eventInfo) {
        try {
            FmsExceptionScanLog logRow = new FmsExceptionScanLog();
            logRow.setRuleCode(rule.getRuleCode());
            logRow.setTriggerType("RECHECK");
            logRow.setStartAt(startAt);
            logRow.setFinishAt(new Date());
            logRow.setCandidateCount(outcome == null ? 0 : (int) outcome.getCandidateCount());
            logRow.setHitCount(outcome == null || outcome.getValidHits() == null ? 0 : outcome.getValidHits().size());
            logRow.setInstanceRecovered(recovered);
            logRow.setCostMs((int) (System.currentTimeMillis() - startAt.getTime()));
            logRow.setStepsJson(writeJson(Map.of("steps", outcome == null ? List.of() : outcome.getSteps(),
                    "event", eventInfo, "engineVersion", outcome == null ? null : outcome.getEngineVersion())));
            logRow.setError("SUCCESS".equals(outcome == null ? "" : outcome.getStatus()) ? null
                    : outcome == null ? "复评无结果" : outcome.getError());
            logRow.setTenantCode(rule.getTenantCode());
            logRow.setDelStatus(0);
            Date now = new Date();
            logRow.setCreateAt(now);
            logRow.setUpdateAt(now);
            logRow.setCreator(EngineContextExecutor.SYSTEM_USER_ID);
            logRow.setCreatorName(EngineContextExecutor.SYSTEM_USER_NAME);
            MetaFactory.save(logRow);
        } catch (Exception e) {
            log.warn("RECHECK 留痕失败 ruleCode={}: {}", rule.getRuleCode(), e.getMessage());
        }
    }

    private static List<ListenEntry> parseListen(String json) {
        List<ListenEntry> entries = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return entries;
        }
        try {
            for (Object item : MAPPER.readValue(json, List.class)) {
                if (item instanceof Map<?, ?> m) {
                    ListenEntry entry = new ListenEntry();
                    entry.entity = str(m.get("entity"));
                    entry.role = m.get("role") == null ? "PRIMARY" : str(m.get("role"));
                    entry.refField = str(m.get("refField"));
                    Object operations = m.get("operations");
                    if (operations instanceof List<?> ops) {
                        entry.operations = ops.stream().map(String::valueOf).toList();
                    }
                    entries.add(entry);
                }
            }
        } catch (Exception e) {
            log.warn("listen_entities 解析失败: {}", e.getMessage());
        }
        return entries;
    }

    private static FmsExceptionRule toRule(Map<String, Object> row) {
        FmsExceptionRule rule = new FmsExceptionRule();
        rule.setId(str(row.get("id")));
        rule.setRuleCode(str(row.get("ruleCode")));
        rule.setRuleName(str(row.get("ruleName")));
        rule.setDomainCode(str(row.get("domainCode")));
        rule.setLevel(str(row.get("level")));
        rule.setRuleKind(str(row.get("ruleKind")));
        rule.setEscalationOfRuleCode(str(row.get("escalationOfRuleCode")));
        rule.setTriggerType(str(row.get("triggerType")));
        rule.setCronExpression(str(row.get("cronExpression")));
        rule.setListenEntities(str(row.get("listenEntities")));
        rule.setPipelineJson(str(row.get("pipelineJson")));
        rule.setThresholdJson(str(row.get("thresholdJson")));
        rule.setOwnerSelector(str(row.get("ownerSelector")));
        rule.setStormJson(str(row.get("stormJson")));
        rule.setBizType(str(row.get("bizType")));
        rule.setEngineVersion(str(row.get("engineVersion")));
        rule.setTenantCode(str(row.get("tenantCode")));
        rule.setAppId(str(row.get("appId")));
        rule.setVersion(row.get("version") instanceof Number n ? n.intValue() : 1);
        rule.setDelStatus(0);
        return rule;
    }

    private static String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static final class ListenEntry {
        String entity;
        String role;
        String refField;
        List<String> operations;
    }
}
