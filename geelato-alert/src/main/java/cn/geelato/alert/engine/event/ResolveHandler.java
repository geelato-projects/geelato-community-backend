package cn.geelato.alert.engine.event;

import cn.geelato.alert.engine.RowMappers;
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
 * 组闭环（P-A7）：EXC_RECOVERED 到达且 AGGREGATE 规则命中 → 定位该异常所在在途组 →
 * 组内全部实例终态（RESOLVED/IGNORED）→ 释放 active_group_key；resolve_notify=1 时
 * 落 RESOLVE 记录（组末实例 = 触发本事件的实例，single 形态带 resolvedAt/resolvedType）。
 */
@Slf4j
public class ResolveHandler {

    private final Dao dao;
    private final AlertGrouper grouper;

    public ResolveHandler(@Qualifier("dynamicDao") Dao dao, AlertGrouper grouper) {
        this.dao = dao;
        this.grouper = grouper;
    }

    /**
     * @return 处理了组闭环返回 true（供监听器记录日志）
     */
    public boolean onRecovered(FmsAlertRule rule, String exceptionId) {
        FmsAlert group = findActiveGroup(rule, exceptionId);
        if (group == null) {
            return false;
        }
        List<String> ids = AlertGrouper.parseIds(group.getEventIds());
        if (!allTerminal(ids)) {
            return false;
        }
        // 释放占用 + 按需落恢复通知（PENDING flush_at=now，立即进冲刷链路；静默豁免由 SilenceChecker 的 allowResolve 处理）
        dao.nativeExecute("UPDATE platform_alert SET active_group_key = NULL, update_at = ? WHERE id = ?",
                new Object[]{new Date(), group.getId()});
        if (rule.getResolveNotify() == 1) {
            grouper.acceptResolve(rule, loadInstance(exceptionId));
            log.info("告警组闭环，落 RESOLVE 通知记录 rule={} record={} group={}",
                    rule.getAlertCode(), exceptionId, group.getId());
        } else {
            log.info("告警组闭环（resolve_notify=0 不通知）rule={} group={}", rule.getAlertCode(), group.getId());
        }
        return true;
    }

    /** 组末实例全量行（RESOLVE 快照数据源；实例已删时闭环照常、通知落库硬失败由调用方兜底记录）。 */
    private Map<String, Object> loadInstance(String exceptionId) {
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT " + cn.geelato.alert.engine.SourceSnapshots.SINGLE_COLUMNS
                        + " FROM platform_exception WHERE id = ? AND del_status = 0",
                new Object[]{exceptionId});
        if (rows == null || rows.isEmpty()) {
            throw new IllegalStateException("RESOLVE 通知源实例不存在或已删除: " + exceptionId);
        }
        return rows.get(0);
    }

    /** 该异常所在的在途组代表（active_group_key 非空、event_ids 含该 id；LIKE 粗筛 + 解析复核）。 */
    private FmsAlert findActiveGroup(FmsAlertRule rule, String exceptionId) {
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT * FROM platform_alert WHERE alert_code = ? AND tenant_code = ? "
                        + "AND active_group_key IS NOT NULL AND del_status = 0 "
                        + "AND event_ids LIKE ? ORDER BY create_at DESC LIMIT 1",
                new Object[]{rule.getAlertCode(), rule.getTenantCode(), "%" + exceptionId + "%"});
        if (rows == null) {
            return null;
        }
        for (Map<String, Object> row : rows) {
            FmsAlert alert = RowMappers.toAlert(row);
            if (AlertGrouper.parseIds(alert.getEventIds()).contains(exceptionId)) {
                return alert;
            }
        }
        return null;
    }

    /** 组内全部实例终态（RESOLVED/IGNORED）；实例已删视为终态（不可再告警）。 */
    private boolean allTerminal(List<String> ids) {
        if (ids.isEmpty()) {
            return false;
        }
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            placeholders.append(i == 0 ? "?" : ",?");
        }
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT status FROM platform_exception WHERE id IN (" + placeholders + ") AND del_status = 0",
                ids.toArray());
        List<String> statuses = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                statuses.add(String.valueOf(row.values().stream().findFirst().orElse(null)));
            }
        }
        if (statuses.size() < ids.size()) {
            return true;
        }
        for (String status : statuses) {
            if (!"RESOLVED".equals(status) && !"IGNORED".equals(status)) {
                return false;
            }
        }
        return true;
    }
}
