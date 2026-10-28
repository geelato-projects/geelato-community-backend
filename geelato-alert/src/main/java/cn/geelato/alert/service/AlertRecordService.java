package cn.geelato.alert.service;

import cn.geelato.alert.entity.FmsAlert;
import cn.geelato.alert.support.SessionCtxHelper;
import cn.geelato.core.orm.Dao;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.page.PageResult;
import cn.geelato.orm.query.Filter;
import cn.geelato.orm.query.MetaQuery;
import cn.geelato.orm.query.Order;
import com.fasterxml.jackson.databind.JsonNode;
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
 * 告警记录服务：记录分页 / 详情（渲染快照 + 命中源实例清单）/ 投递明细（biz_key 回查占位）/
 * 手动重发（CAS 抢占，投递引擎留后续）。
 *
 * <p>关联文档：bea/业务告警中心-接口设计-2026-09-22.md §5
 */
@Service
public class AlertRecordService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 可重发的记录状态（接口设计 §5：FAIL/PARTIAL 渲染快照重投）。 */
    private static final List<String> RESENDABLE_STATUS = List.of("FAIL", "PARTIAL");

    @Autowired
    @Qualifier("dynamicDao")
    private Dao dynamicDao;

    /** 引擎缺省关（geelato.alert.engine.enabled=false）时不装配，ObjectProvider 可空退化占位。 */
    @Autowired
    private org.springframework.beans.factory.ObjectProvider<cn.geelato.alert.engine.deliver.DeliverEngine> deliverEngineProvider;

    @Autowired
    private org.springframework.beans.factory.ObjectProvider<cn.geelato.alert.engine.deliver.MessageTransport> messageTransportProvider;

    /** 记录分页（告警规则/触发类型/状态/渠道/接收人/时间窗；接收人视角=发给我的）。 */
    public Map<String, Object> pageQuery(RecordQueryParams params) {
        int page = Math.max(1, params.pageNum);
        int pageSize = Math.min(100, Math.max(1, params.pageSize));
        List<Filter> filters = new ArrayList<>();
        filters.add(Filter.eq("tenantCode", SessionCtxHelper.getCurrentTenantCode()));
        filters.add(Filter.eq("delStatus", 0));
        // 实例记录：appId 为可选过滤（传入则限定归属应用，不传不跨应用兜底也不强制，以免破坏现有实例页）。
        if (notBlank(params.appId)) {
            filters.add(Filter.eq("appId", params.appId.trim()));
        }
        if (notBlank(params.alertCode)) {
            filters.add(Filter.eq("alertCode", params.alertCode.trim()));
        }
        if (notBlank(params.triggerType)) {
            filters.add(Filter.eq("triggerType", params.triggerType.trim().toUpperCase()));
        }
        if (notBlank(params.status)) {
            filters.add(Filter.eq("status", params.status.trim().toUpperCase()));
        } else {
            String[] statusSet = statusSetForScope(params.scope);
            if (statusSet != null) {
                filters.add(Filter.in("status", statusSet));
            }
        }
        if (notBlank(params.channel)) {
            // channels 为 JSON 数组文本列，粗筛 LIKE（无损判定由前端按解析结果复核）
            filters.add(Filter.like("channels", "%\"" + params.channel.trim() + "\"%"));
        }
        if (notBlank(params.receiverId)) {
            filters.add(Filter.like("receiverIds", "%" + params.receiverId.trim() + "%"));
        }
        if (params.startTime != null) {
            filters.add(Filter.ge("createAt", params.startTime));
        }
        if (params.endTime != null) {
            filters.add(Filter.le("createAt", params.endTime));
        }
        MetaQuery query = MetaFactory.query(FmsAlert.class)
                .where(filters.toArray(new Filter[0]))
                .order(Order.desc("createAt"));
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

    /** 按 id 取记录（当前租户）；不存在返回 null。 */
    public FmsAlert getById(String id) {
        MetaQuery query = MetaFactory.query(FmsAlert.class)
                .where(Filter.eq("id", id),
                        Filter.eq("tenantCode", SessionCtxHelper.getCurrentTenantCode()),
                        Filter.eq("delStatus", 0));
        List<Map<String, Object>> rows = query.list();
        return rows == null || rows.isEmpty() ? null : rowToEntity(rows.get(0));
    }

    /** 记录详情：渲染快照 + 源业务数据快照 + 命中源实例清单（清单为源表实时状态回查，快照为落库时刻数据）。 */
    public Map<String, Object> detail(FmsAlert record) {
        Map<String, Object> map = toListRow(record);
        map.put("renderedContent", record.getRenderedContent());
        map.put("stats", parseJsonObjectOrNull(record.getStatsJson()));
        map.put("source", parseJsonObjectOrNull(record.getSourceJson()));
        map.put("receiverIds", parseJsonObjectOrNull(record.getReceiverIds()));
        map.put("receiverNames", parseJsonObjectOrNull(record.getReceiverNames()));
        map.put("batchNo", record.getBatchNo());
        map.put("groupKey", record.getGroupKey());
        map.put("bizKey", record.getBizKey());
        map.put("retryCount", record.getRetryCount());
        map.put("errorMessage", record.getErrorMessage());
        map.put("sentAt", record.getSentAt());
        map.put("exceptions", queryExceptionBriefs(record.getEventIds()));
        return map;
    }

    /**
     * 投递明细（{@code GET /record/{id}/messages}）：biz_key 前缀 × 通道拼完整键
     * （{recordId}:{batchNo}:{channel}）逐条回查 geelato-message platform_msg。
     */
    public Map<String, Object> messages(FmsAlert record) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recordId", record.getId());
        result.put("bizKey", record.getBizKey());
        cn.geelato.alert.engine.deliver.MessageTransport transport =
                messageTransportProvider == null ? null : messageTransportProvider.getIfAvailable();
        if (transport == null) {
            result.put("messageStates", List.of());
            result.put("note", "投递引擎未启用（geelato.alert.engine.enabled），无回查通道");
            return result;
        }
        if (record.getBizKey() == null || record.getBizKey().isBlank()) {
            result.put("messageStates", List.of());
            result.put("note", "尚未投递（biz_key 为空）");
            return result;
        }
        List<Map<String, Object>> states = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (String channel : parseIdList(record.getChannels())) {
            try {
                for (Map<String, Object> row : transport.listByBizKey(record.getBizKey() + ":" + channel)) {
                    Map<String, Object> state = new LinkedHashMap<>();
                    state.put("channel", channel);
                    state.put("msgId", str(row.get("id")));
                    state.put("type", str(row.get("type")));
                    state.put("status", str(row.get("status")));
                    state.put("receiver", str(row.get("receiver")));
                    state.put("retryCount", row.get("retryCount"));
                    state.put("errorMessage", str(row.get("errorMessage")));
                    state.put("deadLetterReason", str(row.get("deadLetterReason")));
                    state.put("lastSendTime", row.get("lastSendTime"));
                    state.put("successTime", row.get("successTime"));
                    states.add(state);
                }
            } catch (Exception e) {
                errors.add(channel + ": " + e.getMessage());
            }
        }
        result.put("messageStates", states);
        if (!errors.isEmpty()) {
            result.put("note", "部分通道回查失败: " + String.join("; ", errors));
        }
        return result;
    }

    /**
     * 手动重发（{@code POST /record/{id}/resend}）：仅 FAIL/PARTIAL；CAS 抢占防并发重复重发，
     * 引擎启用时同步走快照重投（不重渲染不重求解，渠道变体亦用主快照）。
     *
     * @return {@code {success, status, conflictMessage, queued}}；并发抢占失败时 success=false
     */
    public Map<String, Object> resend(FmsAlert record) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!RESENDABLE_STATUS.contains(record.getStatus())) {
            result.put("success", false);
            result.put("status", record.getStatus());
            result.put("conflictMessage", "仅 FAIL/PARTIAL 记录可重发，当前状态: " + record.getStatus());
            return result;
        }
        int rows = dynamicDao.nativeExecute(
                "UPDATE platform_alert SET retry_count = retry_count + 1, update_at = NOW() "
                        + "WHERE id = ? AND status = ? AND del_status = 0",
                new Object[]{record.getId(), record.getStatus()});
        if (rows == 0) {
            result.put("success", false);
            result.put("status", record.getStatus());
            result.put("conflictMessage", "记录状态已被变更（可能另一重发进行中）");
            return result;
        }
        record.setRetryCount(record.getRetryCount() + 1);
        cn.geelato.alert.engine.deliver.DeliverEngine engine =
                deliverEngineProvider == null ? null : deliverEngineProvider.getIfAvailable();
        if (engine == null) {
            result.put("success", true);
            result.put("status", record.getStatus());
            result.put("queued", false);
            result.put("note", "投递引擎未启用，重发已抢占登记但未实际投递");
            return result;
        }
        try {
            engine.deliverOne(record, true);
            result.put("success", true);
            result.put("status", record.getStatus());
            result.put("queued", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("status", record.getStatus());
            result.put("queued", false);
            result.put("conflictMessage", "重投失败: " + e.getMessage());
        }
        return result;
    }

    /** 命中源实例的首个客户 id（EXCEPTION 源，静默预填维度），跨模块原生回查。 */
    public String firstCustomerId(FmsAlert record) {
        List<Map<String, Object>> exceptions = queryExceptionBriefs(record.getEventIds());
        for (Map<String, Object> e : exceptions) {
            Object cid = e.get("customerId");
            if (cid != null && !String.valueOf(cid).isBlank()) {
                return String.valueOf(cid);
            }
        }
        return null;
    }

    // ==================== 内部辅助 ====================

    /** event_ids（JSON 数组文本）→ platform_exception 概要行（EXCEPTION 源回查；跨模块以原生 SQL 只读，避免模块依赖。新源接入时按规则 match.source 分发）。 */
    private List<Map<String, Object>> queryExceptionBriefs(String eventIdsJson) {
        List<String> ids = parseIdList(eventIdsJson);
        if (ids.isEmpty()) {
            return List.of();
        }
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            placeholders.append(i == 0 ? "?" : ",?");
        }
        String sql = "SELECT id, rule_code, rule_name, level, biz_label, title, "
                + "participant_name, first_at, status FROM platform_exception "
                + "WHERE id IN (" + placeholders + ") AND del_status = 0";
        List<Map<String, Object>> rows = dynamicDao.nativeQueryForMapList(sql, ids.toArray());
        List<Map<String, Object>> briefs = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Map<String, Object> brief = new LinkedHashMap<>();
                brief.put("exceptionId", str(pick(row, "id")));
                brief.put("ruleCode", str(pick(row, "rule_code", "ruleCode")));
                brief.put("ruleName", str(pick(row, "rule_name", "ruleName")));
                brief.put("level", str(pick(row, "level")));
                brief.put("bizLabel", str(pick(row, "biz_label", "bizLabel")));
                brief.put("title", str(pick(row, "title")));
                brief.put("participantName", str(pick(row, "participant_name", "participantName")));
                brief.put("firstAt", pick(row, "first_at", "firstAt"));
                brief.put("status", str(pick(row, "status")));
                briefs.add(brief);
            }
        }
        return briefs;
    }

    /** 原生行键名兼容：不同驱动可能返回snake_case 或 camelCase。 */
    private Object pick(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        return null;
    }

    private List<String> parseIdList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            List<String> ids = new ArrayList<>();
            if (node.isArray()) {
                for (JsonNode n : node) {
                    String v = n.asText(null);
                    if (v != null && !v.isBlank()) {
                        ids.add(v.trim());
                    }
                }
            }
            return ids;
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> toListRow(FmsAlert record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", record.getId());
        map.put("alertCode", record.getAlertCode());
        map.put("alertName", record.getAlertName());
        map.put("triggerType", record.getTriggerType());
        map.put("eventType", record.getEventType());
        map.put("batchNo", record.getBatchNo());
        map.put("eventCount", record.getEventCount());
        map.put("renderedTitle", record.getRenderedTitle());
        map.put("receiverIds", parseJsonObjectOrNull(record.getReceiverIds()));
        map.put("receiverNames", parseJsonObjectOrNull(record.getReceiverNames()));
        map.put("channels", parseJsonObjectOrNull(record.getChannels()));
        map.put("status", record.getStatus());
        // 是否已产生消息：status ∈ {SENT, PARTIAL} 表示投递引擎已成功 enqueue 至少一路通道（biz_key 回写）。
        map.put("hasMessage", messageProduced(record.getStatus()));
        map.put("sentAt", record.getSentAt());
        map.put("createAt", record.getCreateAt());
        return map;
    }

    private FmsAlert rowToEntity(Map<String, Object> row) {
        FmsAlert r = new FmsAlert();
        r.setId(str(row.get("id")));
        r.setAlertCode(str(row.get("alertCode")));
        r.setAlertName(str(row.get("alertName")));
        r.setAlertVersion(intObj(row.get("alertVersion")));
        r.setTriggerType(str(row.get("triggerType")));
        r.setEventType(str(row.get("eventType")));
        r.setBatchNo(str(row.get("batchNo")));
        r.setGroupKey(str(row.get("groupKey")));
        r.setActiveGroupKey(str(row.get("activeGroupKey")));
        r.setEventCount(intVal(row.get("eventCount")));
        r.setEventIds(str(row.get("eventIds")));
        r.setSourceJson(str(row.get("sourceJson")));
        r.setStatsJson(str(row.get("statsJson")));
        r.setRenderedTitle(str(row.get("renderedTitle")));
        r.setRenderedContent(str(row.get("renderedContent")));
        r.setReceiverIds(str(row.get("receiverIds")));
        r.setReceiverNames(str(row.get("receiverNames")));
        r.setChannels(str(row.get("channels")));
        r.setStatus(str(row.get("status")));
        r.setSilenceId(str(row.get("silenceId")));
        r.setFlushAt(toDate(row.get("flushAt")));
        r.setFirstEventAt(toDate(row.get("firstEventAt")));
        r.setLastEventAt(toDate(row.get("lastEventAt")));
        r.setLastSentAt(toDate(row.get("lastSentAt")));
        r.setBizKey(str(row.get("bizKey")));
        r.setRetryCount(intVal(row.get("retryCount")));
        r.setErrorMessage(str(row.get("errorMessage")));
        r.setSentAt(toDate(row.get("sentAt")));
        r.setCreateAt(toDate(row.get("createAt")));
        r.setUpdateAt(toDate(row.get("updateAt")));
        r.setAppId(str(row.get("appId")));
        r.setTenantCode(str(row.get("tenantCode")));
        r.setDelStatus(intVal(row.get("delStatus")));
        return r;
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

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * scope → 状态集解析（用户实例弹窗页签）：HISTORY 归已投递/半投递/失败/静默终态；
     * ACTIVE 归待聚合/待投递；缺省返回 null 表示不施加 status 过滤（保持现有行为）。
     * 显式 status 精确过滤优先，不经过本方法。包级可见以便离线单测覆盖分支映射。
     */
    static String[] statusSetForScope(String scope) {
        if ("HISTORY".equalsIgnoreCase(scope)) {
            return new String[]{"SENT", "PARTIAL", "FAIL", "SILENCED"};
        }
        if ("ACTIVE".equalsIgnoreCase(scope)) {
            return new String[]{"PENDING", "READY"};
        }
        return null;
    }

    /** 告警是否已产生消息：status ∈ {SENT, PARTIAL} 表示已成功 enqueue 至少一路通道消息。 */
    static boolean messageProduced(String status) {
        return "SENT".equalsIgnoreCase(status) || "PARTIAL".equalsIgnoreCase(status);
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

    private Integer intObj(Object o) {
        return o == null ? null : intVal(o);
    }

    private Date toDate(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Date d) {
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

    // ==================== DTO ====================

    /** 记录分页查询参数。 */
    public static class RecordQueryParams {
        public String appId;
        public String alertCode;
        public String triggerType;
        public String status;
        /** 状态集范围：ACTIVE 在途（PENDING/READY）/ HISTORY 历史（SENT/PARTIAL/FAIL/SILENCED）；显式 status 优先。 */
        public String scope;
        public String channel;
        public String receiverId;
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        public Date startTime;
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        public Date endTime;
        public int pageNum = 1;
        public int pageSize = 20;
    }

    /** 从记录创建静默请求（{@code POST /record/{id}/silence-from}）。 */
    @lombok.Data
    public static class SilenceFromRequest {
        /** 静默时长（小时，缺省 24，上限 720）。 */
        private Integer hours;
        private String reason;
    }
}
