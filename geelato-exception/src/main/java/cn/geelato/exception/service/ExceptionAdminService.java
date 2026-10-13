package cn.geelato.exception.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.page.PageResult;
import cn.geelato.orm.query.Filter;
import cn.geelato.orm.query.MetaQuery;
import cn.geelato.orm.query.Order;
import cn.geelato.exception.entity.FmsExceptionScanLog;
import cn.geelato.exception.support.SessionCtxHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 异常管理 / 事件接入服务。
 *
 * <p>契约面 + 持久化深度：
 * <ul>
 *   <li>{@code entity-event}：入参归一校验 + {@code eventId} 幂等去重（复用 scan_log 唯一键落 raw 留痕）；
 *       复评/建实例引擎留后续，端点恒成功。</li>
 *   <li>{@code admin/scan}：端点存在、写 scan_log 留痕，但不执行扫描管道（{@code executed=false} 占位）。</li>
 *   <li>{@code admin/scan-logs} / {@code admin/archive}：读写走持久化。</li>
 * </ul>
 *
 * <p>关联文档：bea/业务异常预警中心-接口设计-2026-09-17.md §3、§5
 */
@Service
public class ExceptionAdminService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    @Qualifier("dynamicDao")
    private Dao dynamicDao;

    @Autowired
    private ObjectProvider<cn.geelato.exception.engine.scan.ScanRunner> scanRunnerProvider;

    @Autowired
    private ObjectProvider<cn.geelato.exception.engine.trigger.ChangeEventDispatcher> eventDispatcherProvider;

    /**
     * 事件接入（{@code POST /entity-event}，鉴权由平台 SystemToken 拦截器处理）。
     *
     * <p>归一化入参校验 + {@code eventId} 幂等去重（复用 scan_log 唯一键落 raw 留痕）；
     * 引擎可用时异步提交复评（listen 匹配 → RECHECK → 建实例/恢复，扫描兜底），不可用时恒成功仅留痕。
     *
     * @return {@code {accepted, duplicated, eventId}}（含引擎可用时的 {@code recheckDispatched}）
     */
    public Map<String, Object> entityEvent(EntityEventRequest req) {
        if (req == null || isBlank(req.getEventId())) {
            throw new IllegalArgumentException("eventId 不能为空");
        }
        if (isBlank(req.getEntityName())) {
            throw new IllegalArgumentException("entityName 不能为空");
        }
        boolean duplicated = existsEvent(req.getEventId());
        if (duplicated) {
            return result(true, true, req.getEventId());
        }
        FmsExceptionScanLog log = new FmsExceptionScanLog();
        log.setRuleCode(req.getRuleCode() == null ? "" : req.getRuleCode());
        log.setTriggerType("RECHECK");
        log.setEventId(req.getEventId());
        log.setStartAt(new Date());
        log.setStepsJson(writeJson(Map.of(
                "kind", "ENTITY_EVENT_RAW",
                "entityName", nz(req.getEntityName()),
                "operation", nz(req.getOperation()),
                "bizId", nz(req.getBizId()),
                "eventTenant", nz(req.getTenantCode()),
                "eventTime", nz(req.getEventTime()))));
        boolean dispatched = false;
        cn.geelato.exception.engine.trigger.ChangeEventDispatcher dispatcher = eventDispatcherProvider == null
                ? null : eventDispatcherProvider.getIfAvailable();
        if (dispatcher != null) {
            dispatched = dispatcher.dispatch(req.getEntityName(), req.getOperation(), req.getBizId(),
                    req.getTenantCode() == null || req.getTenantCode().isBlank()
                            ? SessionCtxHelper.getCurrentTenantCode() : req.getTenantCode(),
                    req.getEventId());
            log.setError(null);
        } else {
            log.setError("复评引擎未启用，仅 raw 留痕");
        }
        fillNewAudit(log);
        MetaFactory.save(log);
        Map<String, Object> out = result(true, false, req.getEventId());
        out.put("recheckDispatched", dispatched);
        return out;
    }

    /**
     * 手动触发扫描（{@code POST /admin/scan}）：引擎可用时同步执行单规则/全部启用规则的真实扫描管道
     * （CAS 抢占、结果回填 scan_log）；不可用时仅留痕（executed=false）。
     */
    public Map<String, Object> adminScan(ScanRequest req) {
        boolean all = req != null && Boolean.TRUE.equals(req.getAll());
        String ruleCode = req == null ? null : req.getRuleCode();
        List<cn.geelato.exception.entity.FmsExceptionRule> targets = new ArrayList<>();
        if (all) {
            MetaQuery query = MetaFactory.query(cn.geelato.exception.entity.FmsExceptionRule.class)
                    .where(Filter.eq("tenantCode", SessionCtxHelper.getCurrentTenantCode()),
                            Filter.eq("delStatus", 0),
                            Filter.eq("enableStatus", 1));
            List<Map<String, Object>> rows = query.list();
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    targets.add(toRule(row));
                }
            }
        } else if (!isBlank(ruleCode)) {
            MetaQuery query = MetaFactory.query(cn.geelato.exception.entity.FmsExceptionRule.class)
                    .where(Filter.eq("tenantCode", SessionCtxHelper.getCurrentTenantCode()),
                            Filter.eq("delStatus", 0),
                            Filter.eq("ruleCode", ruleCode.trim()));
            List<Map<String, Object>> rows = query.list();
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    targets.add(toRule(row));
                }
            }
        }
        cn.geelato.exception.engine.scan.ScanRunner runner = scanRunnerProvider == null
                ? null : scanRunnerProvider.getIfAvailable();
        List<Object> results = new ArrayList<>();
        if (runner != null) {
            for (cn.geelato.exception.entity.FmsExceptionRule target : targets) {
                results.add(runner.runOneScanSync(target));
            }
        } else {
            Date now = new Date();
            for (cn.geelato.exception.entity.FmsExceptionRule target : targets) {
                FmsExceptionScanLog log = new FmsExceptionScanLog();
                log.setRuleCode(target.getRuleCode());
                log.setStartAt(now);
                log.setFinishAt(now);
                log.setError("扫描管道引擎未启用");
                fillNewAudit(log);
                MetaFactory.save(log);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executed", runner != null && !targets.isEmpty());
        result.put("scannedRuleCount", targets.size());
        result.put("ruleCodes", targets.stream().map(cn.geelato.exception.entity.FmsExceptionRule::getRuleCode).toList());
        result.put("results", results);
        if (runner == null) {
            result.put("note", "扫描执行引擎未启用，仅留痕");
        }
        return result;
    }

    private static cn.geelato.exception.entity.FmsExceptionRule toRule(Map<String, Object> row) {
        cn.geelato.exception.entity.FmsExceptionRule rule = new cn.geelato.exception.entity.FmsExceptionRule();
        rule.setId(str(row.get("id")));
        rule.setRuleCode(str(row.get("ruleCode")));
        rule.setRuleName(str(row.get("ruleName")));
        rule.setDomainCode(str(row.get("domainCode")));
        rule.setLevel(str(row.get("level")));
        rule.setRuleKind(str(row.get("ruleKind")));
        rule.setTriggerType(str(row.get("triggerType")));
        rule.setCronExpression(str(row.get("cronExpression")));
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

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** 扫描日志分页（{@code GET /admin/scan-logs}）。 */
    public Map<String, Object> scanLogs(ScanLogQuery query) {
        int page = Math.max(1, query.pageNum);
        int pageSize = Math.min(100, Math.max(1, query.pageSize));
        List<Filter> filters = new ArrayList<>();
        filters.add(Filter.eq("tenantCode", SessionCtxHelper.getCurrentTenantCode()));
        filters.add(Filter.eq("delStatus", 0));
        if (!isBlank(query.ruleCode)) {
            filters.add(Filter.eq("ruleCode", query.ruleCode.trim()));
        }
        MetaQuery metaQuery = MetaFactory.query(FmsExceptionScanLog.class)
                .where(filters.toArray(new Filter[0]))
                .order(Order.desc("startAt"));
        PageResult<Map<String, Object>> pageResult = metaQuery.page(page, pageSize).page();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : pageResult.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("ruleCode", row.get("ruleCode"));
            item.put("startAt", row.get("startAt"));
            item.put("finishAt", row.get("finishAt"));
            item.put("candidateCount", row.get("candidateCount"));
            item.put("hitCount", row.get("hitCount"));
            item.put("instanceCreated", row.get("instanceCreated"));
            item.put("instanceRenewed", row.get("instanceRenewed"));
            item.put("instanceRecovered", row.get("instanceRecovered"));
            item.put("suppressedCount", row.get("suppressedCount"));
            item.put("stepsJson", row.get("stepsJson"));
            item.put("costMs", row.get("costMs"));
            item.put("error", row.get("error"));
            item.put("eventId", row.get("eventId"));
            items.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", items);
        result.put("total", pageResult.getTotal());
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("hasMore", (long) page * pageSize < pageResult.getTotal());
        return result;
    }

    /**
     * 归档触发（{@code POST /admin/archive}）：闭环超保留期实例组移归档。
     *
     * <p>契约面：端点存在，统计可归档实例数（{@code status IN RESOLVED/IGNORED 且 resolved_at < 阈值}），
     * 真实归档搬迁引擎留后续，返回 {@code executed=false} + 命中计数。
     */
    public Map<String, Object> archive(ArchiveRequest req) {
        int retentionDays = req != null && req.getRetentionDays() != null && req.getRetentionDays() > 0
                ? req.getRetentionDays() : 90;
        String tenant = SessionCtxHelper.getCurrentTenantCode();
        Date threshold = new Date(System.currentTimeMillis() - retentionDays * 86400000L);
        Long count = dynamicDao.nativeQueryForObject(
                "SELECT COUNT(*) FROM platform_exception WHERE tenant_code=? AND del_status=0"
                        + " AND status IN ('RESOLVED','IGNORED') AND resolved_at IS NOT NULL AND resolved_at < ?",
                new Object[]{tenant, threshold}, Long.class);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executed", false);
        result.put("archivableCount", count == null ? 0L : count);
        result.put("retentionDays", retentionDays);
        result.put("note", "归档搬迁引擎留待后续，本端点仅统计");
        return result;
    }

    // ==================== 内部辅助 ====================

    private boolean existsEvent(String eventId) {
        MetaQuery query = MetaFactory.query(FmsExceptionScanLog.class)
                .where(Filter.eq("eventId", eventId), Filter.eq("delStatus", 0));
        List<Map<String, Object>> rows = query.list();
        return rows != null && !rows.isEmpty();
    }

    private Map<String, Object> result(boolean accepted, boolean duplicated, String eventId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("accepted", accepted);
        map.put("duplicated", duplicated);
        map.put("eventId", eventId);
        return map;
    }

    private void fillNewAudit(FmsExceptionScanLog log) {
        Date now = new Date();
        log.setCandidateCount(0);
        log.setHitCount(0);
        log.setCostMs(0);
        log.setTenantCode(SessionCtxHelper.getCurrentTenantCode());
        log.setDelStatus(0);
        log.setCreateAt(now);
        log.setUpdateAt(now);
        log.setCreator(SessionCtxHelper.getCurrentUserId());
        log.setCreatorName(SessionCtxHelper.getCurrentUserName());
    }

    private String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    // ==================== DTO ====================

    /** 事件接入请求（{@code POST /entity-event}）。 */
    @lombok.Data
    public static class EntityEventRequest {
        private String eventId;
        private String entityName;
        private String operation;
        private String bizId;
        private String tenantCode;
        private String eventTime;
        private String ruleCode;
    }

    /** 手动扫描请求（{@code POST /admin/scan}）。 */
    @lombok.Data
    public static class ScanRequest {
        private String ruleCode;
        private Boolean all;
    }

    /** 扫描日志查询参数。 */
    public static class ScanLogQuery {
        public String ruleCode;
        public int pageNum = 1;
        public int pageSize = 20;
    }

    /** 归档请求（{@code POST /admin/archive}）。 */
    @lombok.Data
    public static class ArchiveRequest {
        private Integer retentionDays;
    }
}
