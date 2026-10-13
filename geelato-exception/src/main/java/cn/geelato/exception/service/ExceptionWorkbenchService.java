package cn.geelato.exception.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.page.PageResult;
import cn.geelato.orm.query.Filter;
import cn.geelato.orm.query.MetaQuery;
import cn.geelato.orm.query.Order;
import cn.geelato.exception.entity.FmsException;
import cn.geelato.exception.entity.FmsExceptionHandle;
import cn.geelato.exception.entity.FmsExceptionRule;
import cn.geelato.exception.entity.FmsExceptionStep;
import cn.geelato.exception.support.SessionCtxHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 异常工作台服务：清单/看板查询、详情与轨迹读取、处置动作（真实持久化 + CAS）。
 *
 * <p>数据权限由平台查询注入器裁剪；本服务按租户 + del_status 过滤，{@code mine} 收窄到当前用户为处理人。
 * 处置动作以状态机 CAS 条件更新（{@code UPDATE ... WHERE status=:from}），冲突返回语义 {@code STATUS_CONFLICT}。
 *
 * <p>关联文档：bea/业务异常预警中心-接口设计-2026-09-17.md §2
 */
@Service
public class ExceptionWorkbenchService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    @Qualifier("dynamicDao")
    private Dao dynamicDao;

    @Autowired
    private ExceptionRuleService ruleService;

    // ==================== 清单 / 看板 ====================

    /** 在途/历史实例分页清单（{@code POST /pageQuery}）。 */
    public Map<String, Object> pageQuery(PageQueryParams params) {
        int page = Math.max(1, params.pageNum);
        int pageSize = Math.min(100, Math.max(1, params.pageSize));
        List<Filter> filters = new ArrayList<>();
        filters.add(Filter.eq("tenantCode", SessionCtxHelper.getCurrentTenantCode()));
        filters.add(Filter.eq("delStatus", 0));
        // 实例清单：appId 为可选过滤（传入则限定归属应用，不传不强制，以免破坏现有实例页）。
        if (notBlank(params.appId)) {
            filters.add(Filter.eq("appId", params.appId.trim()));
        }
        if (notBlank(params.domainCode)) {
            filters.add(Filter.eq("domainCode", params.domainCode.trim()));
        }
        if (notBlank(params.ruleCode)) {
            filters.add(Filter.eq("ruleCode", params.ruleCode.trim()));
        }
        if (notBlank(params.level)) {
            filters.add(Filter.eq("level", params.level.trim()));
        }
        if (notBlank(params.status)) {
            filters.add(Filter.eq("status", params.status.trim()));
        } else {
            filters.add(Filter.in("status", new Object[]{"OPEN", "PROCESSING", "ESCALATED"}));
        }
        if (Boolean.TRUE.equals(params.mine)) {
            filters.add(Filter.eq("participantId", SessionCtxHelper.getCurrentUserId()));
        }
        if (notBlank(params.customerName)) {
            filters.add(Filter.like("customerName", "%" + params.customerName.trim() + "%"));
        }
        if (notBlank(params.bizNo)) {
            filters.add(Filter.eq("bizNo", params.bizNo.trim()));
        }
        MetaQuery query = MetaFactory.query(FmsException.class)
                .where(filters.toArray(new Filter[0]))
                .order(Order.desc("lastSeenAt"), Order.desc("firstAt"));
        PageResult<Map<String, Object>> pageResult = query.page(page, pageSize).page();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : pageResult.getRecords()) {
            items.add(toListRow(rowToEntity(row)));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", items);
        result.put("total", pageResult.getTotal());
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("hasMore", (long) page * pageSize < pageResult.getTotal());
        return result;
    }

    /** 等级汇总卡（双口径：异常数 source_key 去重 / 实例数；今日新增/闭环/闭环率）。 */
    public Map<String, Object> summary() {
        String tenant = SessionCtxHelper.getCurrentTenantCode();
        Long total = dynamicDao.nativeQueryForObject(
                "SELECT COUNT(*) FROM platform_exception WHERE tenant_code=? AND del_status=0",
                new Object[]{tenant}, Long.class);
        Long openCount = dynamicDao.nativeQueryForObject(
                "SELECT COUNT(*) FROM platform_exception WHERE tenant_code=? AND del_status=0"
                        + " AND status IN ('OPEN','PROCESSING','ESCALATED')",
                new Object[]{tenant}, Long.class);
        Long sourceCount = dynamicDao.nativeQueryForObject(
                "SELECT COUNT(DISTINCT source_key) FROM platform_exception WHERE tenant_code=? AND del_status=0"
                        + " AND status IN ('OPEN','PROCESSING','ESCALATED')",
                new Object[]{tenant}, Long.class);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instanceTotal", nvl(total));
        result.put("instanceOpen", nvl(openCount));
        result.put("exceptionOpen", nvl(sourceCount));
        List<Map<String, Object>> byLevel = dynamicDao.nativeQueryForMapList(
                "SELECT level, COUNT(*) AS instanceCount, COUNT(DISTINCT source_key) AS exceptionCount"
                        + " FROM platform_exception WHERE tenant_code=? AND del_status=0"
                        + " AND status IN ('OPEN','PROCESSING','ESCALATED') GROUP BY level",
                new Object[]{tenant});
        result.put("byLevel", byLevel);
        return result;
    }

    /** 六域瓦片（在途数 / 规则数 / 等级分布）。 */
    public List<Map<String, Object>> domainStats() {
        String tenant = SessionCtxHelper.getCurrentTenantCode();
        List<Map<String, Object>> rows = dynamicDao.nativeQueryForMapList(
                "SELECT e.domain_code AS domainCode,"
                        + " COUNT(DISTINCT e.source_key) AS openCount,"
                        + " COUNT(DISTINCT CASE WHEN e.level='P0' THEN e.source_key END) AS p0,"
                        + " COUNT(DISTINCT CASE WHEN e.level='P1' THEN e.source_key END) AS p1,"
                        + " COUNT(DISTINCT CASE WHEN e.level='P2' THEN e.source_key END) AS p2,"
                        + " COUNT(DISTINCT CASE WHEN e.level='P3' THEN e.source_key END) AS p3,"
                        + " (SELECT COUNT(*) FROM platform_exception_rule r WHERE r.domain_code=e.domain_code"
                        + "   AND r.tenant_code=? AND r.del_status=0 AND r.enable_status=1) AS ruleCount"
                        + " FROM platform_exception e WHERE e.tenant_code=? AND e.del_status=0"
                        + " AND e.status IN ('OPEN','PROCESSING','ESCALATED')"
                        + " GROUP BY e.domain_code",
                new Object[]{tenant, tenant});
        return rows == null ? List.of() : rows;
    }

    // ==================== 详情 / 轨迹 ====================

    /** 按 id 取实例（当前租户）；不存在返回 null。 */
    public FmsException getById(String id) {
        MetaQuery query = MetaFactory.query(FmsException.class)
                .where(Filter.eq("id", id),
                        Filter.eq("tenantCode", SessionCtxHelper.getCurrentTenantCode()),
                        Filter.eq("delStatus", 0));
        List<Map<String, Object>> rows = query.list();
        return rows == null || rows.isEmpty() ? null : rowToEntity(rows.get(0));
    }

    /** 实例详情：基础字段 + detail_json 快照 + action_urls + 当前步骤。 */
    public Map<String, Object> detail(FmsException ex) {
        Map<String, Object> map = toListRow(ex);
        map.put("bizId", ex.getBizId());
        map.put("participantId", ex.getParticipantId());
        map.put("sourceKey", ex.getSourceKey());
        map.put("ruleVersion", ex.getRuleVersion());
        map.put("detailJson", parseJsonObjectOrNull(ex.getDetailJson()));
        map.put("resolvedAt", ex.getResolvedAt() == null ? null : ex.getResolvedAt().toInstant().toString());
        map.put("resolvedType", ex.getResolvedType());
        map.put("actionUrls", buildActionUrls(ex));
        return map;
    }

    /** 处置步骤序列（{@code GET /{id}/steps}）。 */
    public List<Map<String, Object>> steps(String exceptionId) {
        MetaQuery query = MetaFactory.query(FmsExceptionStep.class)
                .where(Filter.eq("exceptionId", exceptionId), Filter.eq("delStatus", 0))
                .order(Order.asc("seq"));
        List<Map<String, Object>> rows = query.list();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("id", str(row.get("id")));
            step.put("stepCode", str(row.get("stepCode")));
            step.put("stepName", str(row.get("stepName")));
            step.put("seq", intVal(row.get("seq")));
            step.put("status", str(row.get("status")));
            step.put("doneAction", str(row.get("doneAction")));
            step.put("assignedAt", isoInstant(row.get("assignedAt")));
            step.put("doneAt", isoInstant(row.get("doneAt")));
            step.put("timeoutAt", isoInstant(row.get("timeoutAt")));
            step.put("escalateCount", intVal(row.get("escalateCount")));
            items.add(step);
        }
        return items;
    }

    /** 处理轨迹流水（{@code GET /{id}/handles}，按 create_at 正序）。 */
    public List<Map<String, Object>> handles(String exceptionId) {
        MetaQuery query = MetaFactory.query(FmsExceptionHandle.class)
                .where(Filter.eq("exceptionId", exceptionId), Filter.eq("delStatus", 0))
                .order(Order.asc("createAt"));
        List<Map<String, Object>> rows = query.list();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("id", str(row.get("id")));
            h.put("action", str(row.get("action")));
            h.put("operatorType", str(row.get("operatorType")));
            h.put("operatorName", str(row.get("operatorName")));
            h.put("beforeStatus", str(row.get("beforeStatus")));
            h.put("afterStatus", str(row.get("afterStatus")));
            h.put("reasonCode", str(row.get("reasonCode")));
            h.put("remark", str(row.get("remark")));
            h.put("createAt", isoInstant(row.get("createAt")));
            items.add(h);
        }
        return items;
    }


    // ==================== 处置动作（CAS）====================

    /** 认领 OPEN → PROCESSING。 */
    public ActionOutcome claim(FmsException ex) {
        return transition(ex, "OPEN", "PROCESSING", "CLAIM", null, ex.getCurrentStepCode(), null);
    }

    /** 处理备注（状态不变，仅追加流水）。 */
    public ActionOutcome note(FmsException ex, String remark) {
        appendHandle(ex.getId(), "HANDLE_NOTE", ex.getStatus(), ex.getStatus(), null, remark);
        return ActionOutcome.ok(ex.getStatus(), ex.getCurrentStepCode());
    }

    /** 解决 → RESOLVED，按 resolve_policy 组联动。 */
    public ActionOutcome resolve(FmsException ex, String remark) {
        String resolvePolicy = resolvePolicyOf(ex.getRuleCode(), ex.getAppId());
        String from = ex.getStatus();
        ActionOutcome outcome = transition(ex, from, "RESOLVED", "RESOLVE", null, ex.getCurrentStepCode(), remark);
        if (!outcome.success) {
            return outcome;
        }
        if ("ANY".equalsIgnoreCase(resolvePolicy)) {
            closeGroupSiblings(ex.getSourceKey());
        }
        return ActionOutcome.ok("RESOLVED", ex.getCurrentStepCode());
    }

    /** 忽略 → IGNORED（reasonCode 必填由 Controller 校验）。 */
    public ActionOutcome ignore(FmsException ex, String reasonCode, String remark) {
        return transition(ex, ex.getStatus(), "IGNORED", "IGNORE", reasonCode, ex.getCurrentStepCode(), remark);
    }

    /** 手动升级：escalation_level +1，状态置 ESCALATED。 */
    public ActionOutcome escalate(FmsException ex, String remark) {
        String from = ex.getStatus();
        int newLevel = ex.getEscalationLevel() + 1;
        int rows = dynamicDao.nativeExecute(
                "UPDATE platform_exception SET status='ESCALATED', escalation_level=?, update_at=?, updater=?"
                        + " WHERE id=? AND status=? AND del_status=0",
                new Object[]{newLevel, new Date(), SessionCtxHelper.getCurrentUserId(), ex.getId(), from});
        if (rows == 0) {
            return ActionOutcome.conflict("该异常状态已被变更，请刷新后重试");
        }
        appendHandle(ex.getId(), "ESCALATE", from, "ESCALATED", null, remark);
        return ActionOutcome.ok("ESCALATED", ex.getCurrentStepCode());
    }

    /** 通用 CAS 状态迁移 + 流水追加。 */
    private ActionOutcome transition(FmsException ex, String from, String to, String action,
                                     String reasonCode, String currentStepCode, String remark) {
        Date now = new Date();
        String userId = SessionCtxHelper.getCurrentUserId();
        StringBuilder sql = new StringBuilder("UPDATE platform_exception SET status=?, update_at=?, updater=?");
        List<Object> args = new ArrayList<>();
        args.add(to);
        args.add(now);
        args.add(userId);
        if ("RESOLVED".equals(to) || "IGNORED".equals(to)) {
            sql.append(", resolved_at=?, resolved_type=?, active_fingerprint=NULL");
            args.add(now);
            args.add("RESOLVED".equals(to) ? "MANUAL" : "IGNORED");
        }
        if (currentStepCode != null) {
            sql.append(", current_step_code=?");
            args.add(currentStepCode);
        }
        sql.append(" WHERE id=? AND status=? AND del_status=0");
        args.add(ex.getId());
        args.add(from);
        int rows = dynamicDao.nativeExecute(sql.toString(), args.toArray());
        if (rows == 0) {
            return ActionOutcome.conflict("该异常状态已被变更，请刷新后重试");
        }
        appendHandle(ex.getId(), action, from, to, reasonCode, remark);
        return ActionOutcome.ok(to, currentStepCode);
    }

    /** ANY 策略组联动：同 source_key 其余在途实例一并置 RESOLVED（GROUP）。 */
    private void closeGroupSiblings(String sourceKey) {
        if (!notBlank(sourceKey)) {
            return;
        }
        Date now = new Date();
        String userId = SessionCtxHelper.getCurrentUserId();
        dynamicDao.nativeExecute(
                "UPDATE platform_exception SET status='RESOLVED', resolved_at=?, resolved_type='GROUP',"
                        + " active_fingerprint=NULL, update_at=?, updater=?"
                        + " WHERE source_key=? AND del_status=0 AND status IN ('OPEN','PROCESSING','ESCALATED')",
                new Object[]{now, now, userId, sourceKey});
    }

    private void appendHandle(String exceptionId, String action, String before, String after,
                              String reasonCode, String remark) {
        Date now = new Date();
        FmsExceptionHandle handle = new FmsExceptionHandle();
        handle.setExceptionId(exceptionId);
        handle.setAction(action);
        handle.setOperatorType("USER");
        handle.setOperatorId(SessionCtxHelper.getCurrentUserId());
        handle.setOperatorName(SessionCtxHelper.getCurrentUserName());
        handle.setBeforeStatus(before);
        handle.setAfterStatus(after);
        handle.setReasonCode(reasonCode);
        handle.setRemark(remark);
        handle.setTenantCode(SessionCtxHelper.getCurrentTenantCode());
        handle.setDelStatus(0);
        handle.setCreateAt(now);
        handle.setUpdateAt(now);
        handle.setCreator(handle.getOperatorId());
        handle.setCreatorName(handle.getOperatorName());
        MetaFactory.save(handle);
    }

    // ==================== 内部辅助 ====================

    /** 取规则的 resolve_policy（规则缺失时默认 SELF，不做组联动）。 */
    private String resolvePolicyOf(String ruleCode, String appId) {
        if (ruleCode == null) {
            return "SELF";
        }
        FmsExceptionRule rule = ruleService.getByCode(ruleCode, appId);
        return rule == null || rule.getResolvePolicy() == null ? "SELF" : rule.getResolvePolicy();
    }

    /** 去处理跳转：按规则 biz_url_template 占位替换实例 biz 字段。 */
    private List<Map<String, Object>> buildActionUrls(FmsException ex) {
        List<Map<String, Object>> urls = new ArrayList<>();
        if (ex.getRuleCode() == null) {
            return urls;
        }
        FmsExceptionRule rule = ruleService.getByCode(ex.getRuleCode(), ex.getAppId());
        String template = rule == null ? null : rule.getBizUrlTemplate();
        if (template == null || template.isBlank()) {
            return urls;
        }
        String url = template
                .replace("{bizId}", nullToEmpty(ex.getBizId()))
                .replace("{bizNo}", nullToEmpty(ex.getBizNo()));
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("label", "去处理");
        action.put("url", url);
        urls.add(action);
        return urls;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private Map<String, Object> toListRow(FmsException ex) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", ex.getId());
        map.put("ruleCode", ex.getRuleCode());
        map.put("ruleName", ex.getRuleName());
        map.put("domainCode", ex.getDomainCode());
        map.put("level", ex.getLevel());
        map.put("status", ex.getStatus());
        map.put("participantName", ex.getParticipantName());
        map.put("bizType", ex.getBizType());
        map.put("bizNo", ex.getBizNo());
        map.put("customerName", ex.getCustomerName());
        map.put("anchorElapsed", ex.getAnchorElapsed());
        map.put("hitBrief", ex.getHitBrief());
        map.put("currentStepCode", ex.getCurrentStepCode());
        map.put("escalationLevel", ex.getEscalationLevel());
        map.put("firstAt", ex.getFirstAt() == null ? null : fmt(ex.getFirstAt()));
        return map;
    }

    private FmsException rowToEntity(Map<String, Object> row) {
        FmsException ex = new FmsException();
        ex.setId(str(row.get("id")));
        ex.setRuleCode(str(row.get("ruleCode")));
        ex.setRuleName(str(row.get("ruleName")));
        ex.setDomainCode(str(row.get("domainCode")));
        ex.setLevel(str(row.get("level")));
        ex.setActiveFingerprint(str(row.get("activeFingerprint")));
        ex.setSourceKey(str(row.get("sourceKey")));
        ex.setParticipantId(str(row.get("participantId")));
        ex.setParticipantName(str(row.get("participantName")));
        ex.setParticipantOrg(str(row.get("participantOrg")));
        ex.setBizType(str(row.get("bizType")));
        ex.setBizId(str(row.get("bizId")));
        ex.setBizNo(str(row.get("bizNo")));
        ex.setCustomerId(str(row.get("customerId")));
        ex.setCustomerName(str(row.get("customerName")));
        ex.setStatus(str(row.get("status")));
        ex.setCurrentStepCode(str(row.get("currentStepCode")));
        ex.setEscalationLevel(intVal(row.get("escalationLevel")));
        ex.setFirstAt(toDate(row.get("firstAt")));
        ex.setLastSeenAt(toDate(row.get("lastSeenAt")));
        ex.setHitCount(intVal(row.get("hitCount")));
        Object anchor = row.get("anchorElapsed");
        ex.setAnchorElapsed(anchor == null ? null : longVal(anchor));
        ex.setHitBrief(str(row.get("hitBrief")));
        ex.setDetailJson(str(row.get("detailJson")));
        ex.setResolvedAt(toDate(row.get("resolvedAt")));
        ex.setResolvedType(str(row.get("resolvedType")));
        Object rv = row.get("ruleVersion");
        ex.setRuleVersion(rv == null ? null : intVal(rv));
        ex.setAppId(str(row.get("appId")));
        ex.setTenantCode(str(row.get("tenantCode")));
        ex.setDelStatus(intVal(row.get("delStatus")));
        return ex;
    }

    private Object parseJsonValue(Object o) {
        if (o == null) {
            return null;
        }
        return parseJsonObjectOrNull(String.valueOf(o));
    }

    private Object parseJsonObjectOrNull(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }

    private String isoInstant(Object o) {
        Date d = toDate(o);
        return d == null ? null : d.toInstant().toString();
    }

    /** 清单时间口径 {@code yyyy-MM-dd HH:mm:ss}。 */
    private String fmt(Date date) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }

    private long nvl(Long v) {
        return v == null ? 0L : v;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private int intVal(Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long longVal(Object o) {
        if (o == null) {
            return 0L;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private Date toDate(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Date d) {
            return d;
        }
        if (o instanceof java.time.LocalDateTime ldt) {
            return Date.from(ldt.atZone(java.time.ZoneId.systemDefault()).toInstant());
        }
        if (o instanceof java.time.Instant instant) {
            return Date.from(instant);
        }
        return null;
    }

    // ==================== DTO / 结果 ====================

    /** 清单查询参数。 */
    public static class PageQueryParams {
        public String appId;
        public String domainCode;
        public String ruleCode;
        public String level;
        public String status;
        public Boolean mine;
        public String customerName;
        public String bizNo;
        public int pageNum = 1;
        public int pageSize = 20;
    }

    /** 处置动作结果（成功回状态；失败带冲突语义）。 */
    public static class ActionOutcome {
        public final boolean success;
        public final String status;
        public final String currentStepCode;
        public final String conflictMessage;

        private ActionOutcome(boolean success, String status, String currentStepCode, String conflictMessage) {
            this.success = success;
            this.status = status;
            this.currentStepCode = currentStepCode;
            this.conflictMessage = conflictMessage;
        }

        static ActionOutcome ok(String status, String currentStepCode) {
            return new ActionOutcome(true, status, currentStepCode, null);
        }

        static ActionOutcome conflict(String message) {
            return new ActionOutcome(false, null, null, message);
        }
    }
}
