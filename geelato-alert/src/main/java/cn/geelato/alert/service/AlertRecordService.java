package cn.geelato.alert.service;

import cn.geelato.alert.entity.FmsAlertRecord;
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
 * 告警记录服务：记录分页 / 详情（渲染快照 + 命中异常清单）/ 投递明细（biz_key 回查占位）/
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
        MetaQuery query = MetaFactory.query(FmsAlertRecord.class)
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
    public FmsAlertRecord getById(String id) {
        MetaQuery query = MetaFactory.query(FmsAlertRecord.class)
                .where(Filter.eq("id", id),
                        Filter.eq("tenantCode", SessionCtxHelper.getCurrentTenantCode()),
                        Filter.eq("delStatus", 0));
        List<Map<String, Object>> rows = query.list();
        return rows == null || rows.isEmpty() ? null : rowToEntity(rows.get(0));
    }

    /** 记录详情：渲染快照 + 命中异常清单（event_ids 跨模块原生回查 platform_exception 概要行）。 */
    public Map<String, Object> detail(FmsAlertRecord record) {
        Map<String, Object> map = toListRow(record);
        map.put("renderedContent", record.getRenderedContent());
        map.put("stats", parseJsonObjectOrNull(record.getStatsJson()));
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
     * 投递明细（{@code GET /record/{id}/messages}）：biz_key 关联 geelato-message  platform_msg 回查。
     *
     * <p>message 侧回查桥接留后续，返回结构完整空清单 + note。
     */
    public Map<String, Object> messages(FmsAlertRecord record) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recordId", record.getId());
        result.put("bizKey", record.getBizKey());
        result.put("messageStates", List.of());
        result.put("note", "geelato-message 回查桥接（biz_key→platform_msg）留待后续");
        return result;
    }

    /**
     * 手动重发（{@code POST /record/{id}/resend}）：仅 FAIL/PARTIAL；CAS 抢占防并发重复重发，
     * 真实重投（渲染快照重投、不重渲染）引擎留后续。
     *
     * @return {@code {success, status, conflictMessage, queued}}；并发抢占失败时 success=false
     */
    public Map<String, Object> resend(FmsAlertRecord record) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!RESENDABLE_STATUS.contains(record.getStatus())) {
            result.put("success", false);
            result.put("status", record.getStatus());
            result.put("conflictMessage", "仅 FAIL/PARTIAL 记录可重发，当前状态: " + record.getStatus());
            return result;
        }
        int rows = dynamicDao.nativeExecute(
                "UPDATE platform_alert_record SET retry_count = retry_count + 1, update_at = NOW() "
                        + "WHERE id = ? AND status = ? AND del_status = 0",
                new Object[]{record.getId(), record.getStatus()});
        if (rows == 0) {
            result.put("success", false);
            result.put("status", record.getStatus());
            result.put("conflictMessage", "记录状态已被变更（可能另一重发进行中）");
            return result;
        }
        result.put("success", true);
        result.put("status", record.getStatus());
        result.put("queued", false);
        result.put("note", "投递引擎未启用，重发已抢占登记但未实际投递");
        return result;
    }

    /** 命中异常的首个客户 id（静默预填维度），跨模块原生回查。 */
    public String firstCustomerId(FmsAlertRecord record) {
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

    /** event_ids（JSON 数组文本）→ platform_exception 概要行（跨模块以原生 SQL 只读回查，避免模块依赖）。 */
    private List<Map<String, Object>> queryExceptionBriefs(String eventIdsJson) {
        List<String> ids = parseIdList(eventIdsJson);
        if (ids.isEmpty()) {
            return List.of();
        }
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            placeholders.append(i == 0 ? "?" : ",?");
        }
        String sql = "SELECT id, rule_code, rule_name, level, biz_no, customer_id, customer_name, "
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
                brief.put("bizNo", str(pick(row, "biz_no", "bizNo")));
                brief.put("customerId", str(pick(row, "customer_id", "customerId")));
                brief.put("customerName", str(pick(row, "customer_name", "customerName")));
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

    private Map<String, Object> toListRow(FmsAlertRecord record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", record.getId());
        map.put("alertCode", record.getAlertCode());
        map.put("alertName", record.getAlertName());
        map.put("triggerType", record.getTriggerType());
        map.put("eventType", record.getEventType());
        map.put("batchNo", record.getBatchNo());
        map.put("exceptionCount", record.getExceptionCount());
        map.put("renderedTitle", record.getRenderedTitle());
        map.put("receiverNames", parseJsonObjectOrNull(record.getReceiverNames()));
        map.put("channels", parseJsonObjectOrNull(record.getChannels()));
        map.put("status", record.getStatus());
        map.put("sentAt", record.getSentAt());
        map.put("createAt", record.getCreateAt());
        return map;
    }

    private FmsAlertRecord rowToEntity(Map<String, Object> row) {
        FmsAlertRecord r = new FmsAlertRecord();
        r.setId(str(row.get("id")));
        r.setAlertCode(str(row.get("alertCode")));
        r.setAlertName(str(row.get("alertName")));
        r.setAlertVersion(intObj(row.get("alertVersion")));
        r.setTriggerType(str(row.get("triggerType")));
        r.setEventType(str(row.get("eventType")));
        r.setBatchNo(str(row.get("batchNo")));
        r.setGroupKey(str(row.get("groupKey")));
        r.setActiveGroupKey(str(row.get("activeGroupKey")));
        r.setExceptionCount(intVal(row.get("exceptionCount")));
        r.setEventIds(str(row.get("eventIds")));
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
