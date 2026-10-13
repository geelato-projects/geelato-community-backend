package cn.geelato.alert.service;

import cn.geelato.alert.entity.FmsAlertRecord;
import cn.geelato.alert.entity.FmsAlertSilence;
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
 * 静默规则服务：分页 / 新建编辑（窗口校验）/ 手动解除（CAS ACTIVE→REVOKED）/ 试匹配 / 从记录预填。
 *
 * <p>关联文档：bea/业务告警中心-接口设计-2026-09-22.md §4-§5
 */
@Service
public class AlertSilenceService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    @Qualifier("dynamicDao")
    private Dao dynamicDao;

    /** 静默分页（status=ACTIVE/EXPIRED/REVOKED 筛选；缺省全部）。 */
    public Map<String, Object> pageQuery(SilenceQueryParams params) {
        int page = Math.max(1, params.pageNum);
        int pageSize = Math.min(100, Math.max(1, params.pageSize));
        List<Filter> filters = new ArrayList<>();
        filters.add(Filter.eq("tenantCode", SessionCtxHelper.getCurrentTenantCode()));
        filters.add(Filter.eq("delStatus", 0));
        if (notBlank(params.status)) {
            filters.add(Filter.eq("status", params.status.trim().toUpperCase()));
        }
        if (notBlank(params.keyword)) {
            filters.add(Filter.like("silenceName", "%" + params.keyword.trim() + "%"));
        }
        MetaQuery query = MetaFactory.query(FmsAlertSilence.class)
                .where(filters.toArray(new Filter[0]))
                .order(Order.desc("createAt"));
        PageResult<Map<String, Object>> pageResult = query.page(page, pageSize).page();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : pageResult.getRecords()) {
            items.add(toRow(rowToEntity(row)));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", items);
        result.put("total", pageResult.getTotal());
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("hasMore", (long) page * pageSize < pageResult.getTotal());
        return result;
    }

    /** 按 id 取静默（当前租户）；不存在返回 null。 */
    public FmsAlertSilence getById(String id) {
        MetaQuery query = MetaFactory.query(FmsAlertSilence.class)
                .where(Filter.eq("id", id),
                        Filter.eq("tenantCode", SessionCtxHelper.getCurrentTenantCode()),
                        Filter.eq("delStatus", 0));
        List<Map<String, Object>> rows = query.list();
        return rows == null || rows.isEmpty() ? null : rowToEntity(rows.get(0));
    }

    /**
     * 新建/编辑静默（{@code POST /silence/save}）：reason 必填；
     * ONCE 窗须 startAt&lt;endAt；RECURRING 须 timeWindow 且总区间有效。
     */
    public FmsAlertSilence save(SilenceSaveRequest req) {
        if (!notBlank(req.getReason())) {
            throw new IllegalArgumentException("静默原因必填");
        }
        String windowType = orDefault(req.getWindowType(), "ONCE").toUpperCase();
        if (!List.of("ONCE", "RECURRING").contains(windowType)) {
            throw new IllegalArgumentException("windowType 应为 ONCE/RECURRING");
        }
        Date startAt = req.getStartAt();
        Date endAt = req.getEndAt();
        if (startAt == null || endAt == null) {
            throw new IllegalArgumentException("生效区间必填（startAt/endAt）");
        }
        if (!startAt.before(endAt)) {
            throw new IllegalArgumentException("startAt 须早于 endAt");
        }
        if ("RECURRING".equals(windowType) && !notBlank(req.getTimeWindow())) {
            throw new IllegalArgumentException("RECURRING 须配置周期窗 timeWindow");
        }
        Date now = new Date();
        String userId = SessionCtxHelper.getCurrentUserId();
        String userName = SessionCtxHelper.getCurrentUserName();
        FmsAlertSilence silence;
        if (notBlank(req.getId())) {
            silence = getById(req.getId());
            if (silence == null) {
                throw new IllegalArgumentException("静默不存在: " + req.getId());
            }
            if ("REVOKED".equals(silence.getStatus())) {
                throw new IllegalStateException("已解除的静默不可编辑");
            }
            silence.setUpdateAt(now);
            silence.setUpdater(userId);
            silence.setUpdaterName(userName);
        } else {
            silence = new FmsAlertSilence();
            silence.setDelStatus(0);
            silence.setCreateAt(now);
            silence.setCreator(userId);
            silence.setCreatorName(userName);
            silence.setUpdateAt(now);
            silence.setUpdater(userId);
            silence.setUpdaterName(userName);
            silence.setStatus("ACTIVE");
            silence.setTenantCode(SessionCtxHelper.getCurrentTenantCode());
        }
        silence.setSilenceName(orEmpty(req.getSilenceName()));
        silence.setMatchJson(req.getMatchJson() == null ? null : writeJson(req.getMatchJson()));
        silence.setWindowType(windowType);
        silence.setStartAt(startAt);
        silence.setEndAt(endAt);
        silence.setTimeWindow(nullToBlank(req.getTimeWindow()));
        silence.setReason(req.getReason().trim());
        silence.setAllowResolve(Boolean.FALSE.equals(req.getAllowResolve()) ? 0 : 1);
        String savedId = MetaFactory.save(silence);
        if (silence.getId() == null && savedId != null) {
            silence.setId(savedId);
        }
        return silence;
    }

    /**
     * 手动解除（{@code POST /silence/{id}/revoke}）：CAS ACTIVE→REVOKED，立即生效。
     *
     * @return 影响行数（0 表示状态已被变更，调用方映射 STATUS_CONFLICT）
     */
    public int revoke(String id) {
        return dynamicDao.nativeExecute(
                "UPDATE platform_alert_silence SET status = 'REVOKED', update_at = NOW() "
                        + "WHERE id = ? AND status = 'ACTIVE' AND del_status = 0",
                new Object[]{id});
    }

    /**
     * 静默试匹配（{@code POST /silence/match-preview}）：给定假想告警维度 → 命中哪些生效中的静默。
     *
     * <p>匹配语义：生效中（ACTIVE 且当前时刻在总窗内）+ 三维度（alertCodes/ruleCodes/customerIds）
     * 声明了该维度则须包含，未声明视为不设限。
     */
    public List<Map<String, Object>> matchPreview(Map<String, Object> hypothetical) {
        String alertCode = str(hypothetical == null ? null : hypothetical.get("alertCode"));
        String ruleCode = str(hypothetical == null ? null : hypothetical.get("ruleCode"));
        String customerId = str(hypothetical == null ? null : hypothetical.get("customerId"));
        Date now = new Date();
        MetaQuery query = MetaFactory.query(FmsAlertSilence.class)
                .where(Filter.eq("status", "ACTIVE"),
                        Filter.eq("tenantCode", SessionCtxHelper.getCurrentTenantCode()),
                        Filter.eq("delStatus", 0));
        List<Map<String, Object>> rows = query.list();
        List<Map<String, Object>> matched = new ArrayList<>();
        if (rows == null) {
            return matched;
        }
        for (Map<String, Object> row : rows) {
            FmsAlertSilence s = rowToEntity(row);
            if (!withinWindow(s, now)) {
                continue;
            }
            List<String> hitDimensions = new ArrayList<>();
            JsonNode match = parseDocumentOrNull(s.getMatchJson());
            if (match == null) {
                // 无维度约束 = 全量静默
                hitDimensions.add("ALL");
            } else {
                if (dimensionHit(match.path("alertCodes"), alertCode)) {
                    hitDimensions.add("alertCodes");
                }
                if (dimensionHit(match.path("ruleCodes"), ruleCode)) {
                    hitDimensions.add("ruleCodes");
                }
                if (dimensionHit(match.path("customerIds"), customerId)) {
                    hitDimensions.add("customerIds");
                }
                // 声明了维度但假想告警对应值为空 → 视为该维度不命中，整体不匹配
                boolean declared = match.has("alertCodes") || match.has("ruleCodes") || match.has("customerIds");
                boolean allDeclaredHit = !declared
                        || (dimensionDeclaredHit(match.path("alertCodes"), alertCode)
                        && dimensionDeclaredHit(match.path("ruleCodes"), ruleCode)
                        && dimensionDeclaredHit(match.path("customerIds"), customerId));
                if (!allDeclaredHit) {
                    continue;
                }
            }
            Map<String, Object> m = toRow(s);
            m.put("hitDimensions", hitDimensions);
            matched.add(m);
        }
        return matched;
    }

    /**
     * 从告警记录一键创建静默（{@code POST /record/{id}/silence-from}）：
     * 预填命中维度（该客户该规则），默认 24h ONCE 窗，静默即落库生效。
     * @param customerId 命中异常的客户维度（由记录关联回查后传入，可空）
     */
    public FmsAlertSilence createFromRecord(FmsAlertRecord record, String customerId, Integer hours, String reason) {
        SilenceSaveRequest req = new SilenceSaveRequest();
        Map<String, Object> matchJson = new LinkedHashMap<>();
        matchJson.put("alertCodes", List.of(record.getAlertCode()));
        if (customerId != null && !customerId.isBlank()) {
            matchJson.put("customerIds", List.of(customerId.trim()));
        }
        req.setMatchJson(matchJson);
        req.setSilenceName(record.getAlertName() == null
                ? record.getAlertCode() + " 临时静默" : record.getAlertName() + " 临时静默");
        req.setWindowType("ONCE");
        Date now = new Date();
        req.setStartAt(now);
        int h = hours == null || hours <= 0 ? 24 : Math.min(hours, 720);
        req.setEndAt(new Date(now.getTime() + h * 3600_000L));
        req.setReason(notBlank(reason) ? reason : "从告警记录 " + record.getId() + " 一键静默 " + h + "h");
        req.setAllowResolve(true);
        return save(req);
    }

    // ==================== 窗口与维度判定 ====================

    /** ONCE：绝对窗；RECURRING：总区间 [startAt,endAt] + 周期窗（days + 每日时段）粗判（分钟精度）。 */
    private boolean withinWindow(FmsAlertSilence s, Date now) {
        if (s.getStartAt() == null || s.getEndAt() == null) {
            return false;
        }
        if (now.before(s.getStartAt()) || now.after(s.getEndAt())) {
            return false;
        }
        if (!"RECURRING".equals(s.getWindowType())) {
            return true;
        }
        JsonNode tw = parseDocumentOrNull(s.getTimeWindow());
        if (tw == null) {
            return true;
        }
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(now);
        JsonNode days = tw.path("days");
        if (days.isArray() && !days.isEmpty()) {
            // 0=周日..6=周六（JS 惯例），Calendar DAY_OF_WEEK 需换算
            int dow = (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7;
            boolean dayHit = false;
            for (JsonNode d : days) {
                if (d.asInt(-1) == dow) {
                    dayHit = true;
                    break;
                }
            }
            if (!dayHit) {
                return false;
            }
        }
        int minutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);
        int start = parseHHmm(tw.path("startTime").asText(null), 0);
        int end = parseHHmm(tw.path("endTime").asText(null), 24 * 60);
        return minutes >= start && minutes < end;
    }

    private int parseHHmm(String text, int def) {
        if (text == null || !text.contains(":")) {
            return def;
        }
        String[] parts = text.split(":");
        try {
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** 未声明维度视为不设限（命中）；声明则须包含。 */
    private boolean dimensionHit(JsonNode arr, String value) {
        if (arr == null || arr.isMissingNode() || arr.isNull() || !arr.isArray() || arr.isEmpty()) {
            return false;
        }
        return notBlank(value) && containsText(arr, value.trim());
    }

    /** 声明了该维度时是否真实命中（未声明恒真）。 */
    private boolean dimensionDeclaredHit(JsonNode arr, String value) {
        if (arr == null || arr.isMissingNode() || arr.isNull() || !arr.isArray() || arr.isEmpty()) {
            return true;
        }
        return notBlank(value) && containsText(arr, value.trim());
    }

    private boolean containsText(JsonNode arr, String value) {
        for (JsonNode n : arr) {
            if (value.equals(n.asText())) {
                return true;
            }
        }
        return false;
    }

    // ==================== 内部辅助 ====================

    private Map<String, Object> toRow(FmsAlertSilence s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", s.getId());
        map.put("silenceName", s.getSilenceName());
        map.put("match", parseJsonObjectOrNull(s.getMatchJson()));
        map.put("windowType", s.getWindowType());
        map.put("startAt", s.getStartAt());
        map.put("endAt", s.getEndAt());
        map.put("timeWindow", parseJsonObjectOrNull(s.getTimeWindow()));
        map.put("reason", s.getReason());
        map.put("allowResolve", s.getAllowResolve() == 1);
        map.put("status", s.getStatus());
        return map;
    }

    private FmsAlertSilence rowToEntity(Map<String, Object> row) {
        FmsAlertSilence s = new FmsAlertSilence();
        s.setId(str(row.get("id")));
        s.setSilenceName(str(row.get("silenceName")));
        s.setMatchJson(str(row.get("matchJson")));
        s.setWindowType(str(row.get("windowType")));
        s.setStartAt(toDate(row.get("startAt")));
        s.setEndAt(toDate(row.get("endAt")));
        s.setTimeWindow(str(row.get("timeWindow")));
        s.setReason(str(row.get("reason")));
        s.setAllowResolve(intVal(row.get("allowResolve")));
        s.setStatus(str(row.get("status")));
        s.setCreateAt(toDate(row.get("createAt")));
        s.setUpdateAt(toDate(row.get("updateAt")));
        s.setTenantCode(str(row.get("tenantCode")));
        s.setDelStatus(intVal(row.get("delStatus")));
        return s;
    }

    private String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    private JsonNode parseDocumentOrNull(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            return null;
        }
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

    private String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private String nullToBlank(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    private String orDefault(String s, String def) {
        return notBlank(s) ? s : def;
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

    /** 静默分页查询参数。 */
    public static class SilenceQueryParams {
        public String status;
        public String keyword;
        public int pageNum = 1;
        public int pageSize = 20;
    }

    /** 静默保存请求（{@code POST /silence/save}）。 */
    @lombok.Data
    public static class SilenceSaveRequest {
        private String id;
        private String silenceName;
        private Map<String, Object> matchJson;
        private String windowType;
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private Date startAt;
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private Date endAt;
        /** 周期窗原文（{days,startTime,endTime}），RECURRING 必填。 */
        private String timeWindow;
        private String reason;
        private Boolean allowResolve;
    }
}
