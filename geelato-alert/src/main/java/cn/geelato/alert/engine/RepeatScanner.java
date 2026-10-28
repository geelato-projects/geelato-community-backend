package cn.geelato.alert.engine;

import cn.geelato.alert.engine.event.AlertGrouper;
import cn.geelato.alert.engine.support.AlertContextExecutor;
import cn.geelato.alert.entity.FmsAlert;
import cn.geelato.alert.entity.FmsAlertRule;
import cn.geelato.core.orm.Dao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 在途组重复提醒扫描（P-A4）：AGGREGATE 规则 repeat_interval_minutes>0 且组最近发送距今到期
 * 且组内仍有在途实例 → 落 REPEAT 记录（eventIds 刷新为当前在途清单，渲染走 group 形态）。
 * 防重：组内已有未完成（PENDING/READY）的 REPEAT 记录则跳过。
 */
@Slf4j
public class RepeatScanner {

    private final Dao dao;
    private final AlertGrouper grouper;

    public RepeatScanner(@Qualifier("dynamicDao") Dao dao, AlertGrouper grouper) {
        this.dao = dao;
        this.grouper = grouper;
    }

    public void tick() {
        List<Map<String, Object>> ruleRows = dao.nativeQueryForMapList(
                "SELECT * FROM platform_alert_rule WHERE enable_status = 1 AND del_status = 0 "
                        + "AND aggregate_mode = 'AGGREGATE' AND trigger_mode IN ('EVENT','BOTH') "
                        + "AND repeat_interval_minutes > 0",
                new Object[]{});
        if (ruleRows == null || ruleRows.isEmpty()) {
            return;
        }
        Date now = new Date();
        for (Map<String, Object> row : ruleRows) {
            try {
                process(RowMappers.toRule(row), now);
            } catch (Exception e) {
                log.warn("REPEAT 扫描异常 rule={}: {}", row.get("alertCode"), e.getMessage());
            }
        }
    }

    private void process(FmsAlertRule rule, Date now) {
        Date lastSentAt = queryGroupLastSentAt(rule);
        if (lastSentAt == null) {
            return;
        }
        long repeatMs = rule.getRepeatIntervalMinutes() * 60_000L;
        if (now.getTime() - lastSentAt.getTime() < repeatMs) {
            return;
        }
        if (hasPendingRepeat(rule)) {
            return;
        }
        List<Map<String, Object>> activeRows = queryActiveRowsOfGroup(rule);
        if (activeRows.isEmpty()) {
            return;
        }
        List<String> activeIds = new ArrayList<>();
        for (Map<String, Object> row : activeRows) {
            activeIds.add(str(row.get("id")));
        }
        FmsAlert record = new FmsAlert();
        record.setAlertCode(rule.getAlertCode());
        record.setAlertName(rule.getAlertName());
        record.setAlertVersion(rule.getVersion());
        record.setTriggerType("REPEAT");
        record.setGroupKey(rule.getAlertCode());
        record.setEventCount(activeIds.size());
        record.setEventIds(AlertGrouper.writeIds(activeIds));
        record.setSourceJson(cn.geelato.alert.engine.SourceSnapshots.writeGroupAll(activeRows));
        record.setStatus("PENDING");
        record.setFirstEventAt(cn.geelato.alert.engine.SourceSnapshots.toDate(pickFirstAt(activeRows)));
        record.setLastEventAt(now);
        record.setFlushAt(now);
        record.setAppId(rule.getAppId());
        record.setTenantCode(rule.getTenantCode());
        AlertGrouper.fillAudit(record, now);
        AlertContextExecutor.withContext(rule.getTenantCode(), () -> cn.geelato.orm.MetaFactory.save(record));
        log.info("REPEAT 到期落记录 rule={} active={}", rule.getAlertCode(), activeIds.size());
    }

    /** 组最近发送（组维度任意记录 MAX(last_sent_at)，含 REPEAT 记录自身）。 */
    private Date queryGroupLastSentAt(FmsAlertRule rule) {
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT MAX(last_sent_at) AS last_sent FROM platform_alert WHERE group_key = ? "
                        + "AND tenant_code = ? AND last_sent_at IS NOT NULL",
                new Object[]{rule.getAlertCode(), rule.getTenantCode()});
        if (rows != null && !rows.isEmpty()) {
            Object v = rows.get(0).containsKey("last_sent") ? rows.get(0).get("last_sent")
                    : rows.get(0).get("lastSent");
            return cn.geelato.alert.engine.SourceSnapshots.toDate(v);
        }
        return null;
    }

    private boolean hasPendingRepeat(FmsAlertRule rule) {
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT id FROM platform_alert WHERE group_key = ? AND tenant_code = ? "
                        + "AND trigger_type = 'REPEAT' AND status IN ('PENDING','READY') AND del_status = 0 LIMIT 1",
                new Object[]{rule.getAlertCode(), rule.getTenantCode()});
        return rows != null && !rows.isEmpty();
    }

    /** 组在途实例全量行（在途组代表记录 eventIds ∩ 仍 OPEN/PROCESSING/ESCALATED，first_at 升序）。 */
    private List<Map<String, Object>> queryActiveRowsOfGroup(FmsAlertRule rule) {
        List<Map<String, Object>> holders = dao.nativeQueryForMapList(
                "SELECT event_ids FROM platform_alert WHERE alert_code = ? AND tenant_code = ? "
                        + "AND active_group_key IS NOT NULL AND del_status = 0 ORDER BY create_at DESC LIMIT 1",
                new Object[]{rule.getAlertCode(), rule.getTenantCode()});
        if (holders == null || holders.isEmpty()) {
            return List.of();
        }
        String eventIds = str(holders.get(0).containsKey("event_ids") ? holders.get(0).get("event_ids")
                : holders.get(0).get("eventIds"));
        List<String> ids = AlertGrouper.parseIds(eventIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            placeholders.append(i == 0 ? "?" : ",?");
        }
        List<Map<String, Object>> actives = dao.nativeQueryForMapList(
                "SELECT " + cn.geelato.alert.engine.SourceSnapshots.GROUP_COLUMNS
                        + " FROM platform_exception WHERE id IN (" + placeholders + ") "
                        + "AND status IN ('OPEN','PROCESSING','ESCALATED') AND del_status = 0 "
                        + "ORDER BY first_at ASC",
                ids.toArray());
        return actives == null ? List.of() : actives;
    }

    /** 首行 first_at（行已按 first_at 升序）。 */
    private static Object pickFirstAt(List<Map<String, Object>> rows) {
        Map<String, Object> first = rows.get(0);
        return first.containsKey("first_at") ? first.get("first_at") : first.get("firstAt");
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
