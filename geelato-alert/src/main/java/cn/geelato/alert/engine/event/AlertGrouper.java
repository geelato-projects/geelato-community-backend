package cn.geelato.alert.engine.event;

import cn.geelato.alert.engine.EngineConsts;
import cn.geelato.alert.engine.RowMappers;
import cn.geelato.alert.engine.SourceSnapshots;
import cn.geelato.alert.entity.FmsAlert;
import cn.geelato.alert.entity.FmsAlertRule;
import cn.geelato.core.orm.Dao;
import cn.geelato.orm.MetaFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 聚合（P-A1 ②/P-A2）：SINGLE(INDEPENDENT) 直通 PENDING（flush_at=now，立即进冲刷链路）；
 * GROUP(AGGREGATE) 在途组追加（eventIds 去重并入）或新建组（groupWait 窗口 + group_interval 顺延 +
 * active_group_key 唯一占用，唯一冲突兜底转追加）。静默/渲染统一由 FlushScanner 收口。
 * 落库时同步采集源实例业务数据进 source_json（渲染/投递不回查源表）。
 */
@Slf4j
public class AlertGrouper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Dao dao;

    public AlertGrouper(@Qualifier("dynamicDao") Dao dao) {
        this.dao = dao;
    }

    /**
     * @param triggerType EVENT（含 RESOLVED 的 INDEPENDENT 规则）/ RESOLVE（AGGREGATE 组闭环通知）
     * @param exceptionRow 源实例全量行（业务快照采集；缺失硬失败）
     */
    public void accept(FmsAlertRule rule, String triggerType, String eventType,
                       String exceptionId, Map<String, Object> exceptionRow) {
        requireRow(exceptionId, exceptionRow);
        if ("AGGREGATE".equals(rule.getAggregateMode())) {
            acceptGroup(rule, triggerType, eventType, exceptionRow);
        } else {
            acceptSingle(rule, triggerType, eventType, exceptionId, exceptionRow);
        }
    }

    // ==================== SINGLE：直通 ====================

    /** RESOLVE 通知记录（single 形态组末实例，不占组——组已闭环释放；快照带 resolvedAt/resolvedType）。 */
    public void acceptResolve(FmsAlertRule rule, Map<String, Object> exceptionRow) {
        requireRow(null, exceptionRow);
        acceptSingle(rule, "RESOLVE", "EXC_RECOVERED",
                SourceSnapshots.str(SourceSnapshots.pick(exceptionRow, "id")),
                exceptionRow);
    }

    private void acceptSingle(FmsAlertRule rule, String triggerType, String eventType,
                              String exceptionId, Map<String, Object> exceptionRow) {
        FmsAlert record = new FmsAlert();
        record.setAlertCode(rule.getAlertCode());
        record.setAlertName(rule.getAlertName());
        record.setAlertVersion(rule.getVersion());
        record.setTriggerType(triggerType);
        record.setEventType(eventType);
        record.setGroupKey(rule.getAlertCode() + "|" + exceptionId);
        record.setEventCount(1);
        record.setEventIds(writeIds(List.of(exceptionId)));
        record.setSourceJson(SourceSnapshots.writeSingle(exceptionRow));
        record.setStatus("PENDING");
        Date now = new Date();
        record.setFirstEventAt(now);
        record.setLastEventAt(now);
        record.setFlushAt(now);
        record.setAppId(rule.getAppId());
        record.setTenantCode(rule.getTenantCode());
        fillAudit(record, now);
        save(record, exceptionRow);
    }

    // ==================== GROUP：在途组追加 / 新建 ====================

    private void acceptGroup(FmsAlertRule rule, String triggerType, String eventType,
                             Map<String, Object> exceptionRow) {
        FmsAlert pending = findPendingGroup(rule);
        if (pending != null && tryAppend(pending, exceptionRow)) {
            return;
        }
        createGroup(rule, triggerType, eventType, exceptionRow);
    }

    /** 在途 PENDING 组（同规则同租户最新一条未冲刷记录）。 */
    private FmsAlert findPendingGroup(FmsAlertRule rule) {
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT * FROM platform_alert WHERE status = 'PENDING' AND alert_code = ? AND tenant_code = ? "
                        + "AND del_status = 0 ORDER BY create_at DESC LIMIT 1",
                new Object[]{rule.getAlertCode(), rule.getTenantCode()});
        return rows == null || rows.isEmpty() ? null : RowMappers.toAlert(rows.get(0));
    }

    /** CAS 追加（0 行=组已被冲刷，转新建下一批）。eventIds 去重并入，source_json 快照同步合并。 */
    private boolean tryAppend(FmsAlert pending, Map<String, Object> exceptionRow) {
        String exceptionId = SourceSnapshots.str(
                SourceSnapshots.pick(exceptionRow, "id"));
        List<String> ids = parseIds(pending.getEventIds());
        if (!ids.contains(exceptionId)) {
            ids.add(exceptionId);
        }
        Date now = new Date();
        int rows = dao.nativeExecute(
                "UPDATE platform_alert SET event_ids = ?, source_json = ?, event_count = ?, last_event_at = ?, update_at = ? "
                        + "WHERE id = ? AND status = 'PENDING' AND del_status = 0",
                new Object[]{writeIds(ids),
                        SourceSnapshots.mergeGroup(pending.getSourceJson(), exceptionRow),
                        ids.size(), now, now, pending.getId()});
        return rows > 0;
    }

    private void createGroup(FmsAlertRule rule, String triggerType, String eventType,
                             Map<String, Object> exceptionRow) {
        String exceptionId = SourceSnapshots.str(
                SourceSnapshots.pick(exceptionRow, "id"));
        String activeKey = activeGroupKeyOf(rule);
        // 清场旧占用（上一批 SENT 后仍持有 active_group_key），本批成为在途组代表
        dao.nativeExecute("UPDATE platform_alert SET active_group_key = NULL WHERE active_group_key = ?",
                new Object[]{activeKey});

        Date now = new Date();
        FmsAlert record = new FmsAlert();
        record.setAlertCode(rule.getAlertCode());
        record.setAlertName(rule.getAlertName());
        record.setAlertVersion(rule.getVersion());
        record.setTriggerType(triggerType);
        record.setEventType(eventType);
        record.setGroupKey(rule.getAlertCode());
        record.setActiveGroupKey(activeKey);
        record.setEventCount(1);
        record.setEventIds(writeIds(List.of(exceptionId)));
        record.setSourceJson(SourceSnapshots.writeGroupAll(List.of(exceptionRow)));
        record.setStatus("PENDING");
        record.setFirstEventAt(now);
        record.setLastEventAt(now);
        record.setFlushAt(flushAtOf(rule, now, now));
        record.setAppId(rule.getAppId());
        record.setTenantCode(rule.getTenantCode());
        fillAudit(record, now);
        try {
            save(record, exceptionRow);
        } catch (Exception e) {
            // 唯一索引兜底并发：他方已建组，转追加
            FmsAlert raced = findPendingGroup(rule);
            if (raced != null && tryAppend(raced, exceptionRow)) {
                return;
            }
            throw e;
        }
    }

    /** 源实例行缺失即硬失败（快照是渲染/投递唯一数据源，不允许无源落库）。 */
    private static void requireRow(String exceptionId, Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            throw new IllegalStateException("源实例行数据缺失，无法采集业务快照: exception=" + exceptionId);
        }
    }

    /**
     * 落库：源行含参与者时以参与者身份保存（审计归属让渡，创建人/修改人=源实例参与者），
     * 无参与者（源行缺失该列）时保持引擎系统身份。
     */
    private static void save(FmsAlert record, Map<String, Object> sourceRow) {
        String participantId = SourceSnapshots.str(SourceSnapshots.pick(sourceRow, "participant_id", "participantId"));
        String participantName = SourceSnapshots.str(SourceSnapshots.pick(sourceRow, "participant_name", "participantName"));
        cn.geelato.alert.engine.support.AlertContextExecutor.withParticipantPrincipal(
                participantId, participantName, () -> MetaFactory.save(record));
    }

    /** 组窗口：firstEventAt + groupWait（缺省 300s）与 上次发送 + groupInterval（缺省 600s）取晚者。 */
    private Date flushAtOf(FmsAlertRule rule, Date firstEventAt, Date now) {
        long waitMs = secondsOf(rule.getGroupWaitSeconds(), 300) * 1000L;
        Date flushAt = new Date(firstEventAt.getTime() + waitMs);
        Date lastSentAt = queryLastSentAt(rule);
        if (lastSentAt != null) {
            long intervalMs = secondsOf(rule.getGroupIntervalSeconds(), 600) * 1000L;
            Date earliest = new Date(lastSentAt.getTime() + intervalMs);
            if (earliest.after(flushAt)) {
                flushAt = earliest;
            }
        }
        return flushAt;
    }

    /** 组最近一次发送时刻（SENT/PARTIAL 的 last_sent_at 最大值）。 */
    private Date queryLastSentAt(FmsAlertRule rule) {
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT MAX(last_sent_at) AS last_sent FROM platform_alert WHERE group_key = ? "
                        + "AND tenant_code = ? AND status IN ('SENT','PARTIAL') AND last_sent_at IS NOT NULL",
                new Object[]{rule.getAlertCode(), rule.getTenantCode()});
        if (rows != null && !rows.isEmpty()) {
            Object v = valueOf(rows.get(0), "last_sent", "lastSent");
            return toDate(v);
        }
        return null;
    }

    static String activeGroupKeyOf(FmsAlertRule rule) {
        return rule.getTenantCode() + "|" + rule.getAlertCode();
    }

    // ==================== 内部辅助 ====================

    private static int secondsOf(Integer value, int def) {
        return value == null || value <= 0 ? def : value;
    }

    private static Object valueOf(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        return null;
    }

    private static Date toDate(Object o) {
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

    public static List<String> parseIds(String json) {
        List<String> ids = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return ids;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = MAPPER.readTree(json);
            if (node.isArray()) {
                Set<String> seen = new LinkedHashSet<>();
                for (com.fasterxml.jackson.databind.JsonNode n : node) {
                    String v = n.asText(null);
                    if (v != null && !v.isBlank()) {
                        seen.add(v.trim());
                    }
                }
                ids.addAll(seen);
            }
        } catch (Exception e) {
            log.warn("event_ids 解析失败: {}", e.getMessage());
        }
        return ids;
    }

    public static String writeIds(List<String> ids) {
        try {
            return MAPPER.writeValueAsString(ids);
        } catch (Exception e) {
            throw new IllegalStateException("event_ids 序列化失败", e);
        }
    }

    public static void fillAudit(FmsAlert record, Date now) {
        record.setDelStatus(0);
        record.setCreateAt(now);
        record.setCreator(EngineConsts.SYSTEM_USER_ID);
        record.setCreatorName(EngineConsts.SYSTEM_USER_NAME);
        record.setUpdateAt(now);
        record.setUpdater(EngineConsts.SYSTEM_USER_ID);
        record.setUpdaterName(EngineConsts.SYSTEM_USER_NAME);
    }
}
