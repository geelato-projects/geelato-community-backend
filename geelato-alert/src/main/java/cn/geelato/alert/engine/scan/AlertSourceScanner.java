package cn.geelato.alert.engine.scan;

import cn.geelato.alert.boot.AlertEngineProperties;
import cn.geelato.alert.engine.RowMappers;
import cn.geelato.alert.engine.event.AlertGrouper;
import cn.geelato.alert.engine.event.AlertMatcher;
import cn.geelato.alert.engine.event.ResolveHandler;
import cn.geelato.alert.engine.support.AlertContextExecutor;
import cn.geelato.alert.entity.FmsAlertRule;
import cn.geelato.core.orm.Dao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 事件源轮询扫描（P-A1 轮询形态，与异常中心 SCAN 同哲学——模块间只以数据库表为媒介，无事件契约）：
 * ① 新增扫描：按 create_at 水位线读 platform_exception 增量，合成 EXC_CREATED 语义 → 匹配 → 聚合；
 * ② 恢复扫描：周期复查在途组与近窗 INDEPENDENT 记录的实例终态，合成 EXC_RECOVERED 语义
 * （AGGREGATE 组闭环复用 ResolveHandler 拉式判定与幂等释放；INDEPENDENT resolve_notify 落 RESOLVE 记录）。
 *
 * <p>幂等：水位线用 {@code >=} 同秒重扫（datetime 秒级精度，同秒后到行不丢），
 * 由 event_ids 存在性去重兜底（重启回看窗口内不重复告警）。
 * 仅合成 EXC_CREATED / EXC_RECOVERED（EXC_CLAIMED 等工作台事件源侧未发布，轮询亦不合成）。
 */
@Slf4j
public class AlertSourceScanner {

    private static final int IN_CHUNK = 100;
    /** INDEPENDENT 恢复复查的记录时间窗（天）：超窗未闭环的不再发恢复通知。 */
    private static final int RESOLVE_WINDOW_DAYS = 7;

    private final Dao dao;
    private final AlertMatcher matcher;
    private final AlertGrouper grouper;
    private final ResolveHandler resolveHandler;
    private final AlertEngineProperties properties;
    /** 已处理到的 platform_exception.create_at 水位线（含边界，同秒重扫由去重兜底）。 */
    private volatile Date watermark;

    public AlertSourceScanner(@Qualifier("dynamicDao") Dao dao, AlertMatcher matcher,
                              AlertGrouper grouper, ResolveHandler resolveHandler,
                              AlertEngineProperties properties) {
        this.dao = dao;
        this.matcher = matcher;
        this.grouper = grouper;
        this.resolveHandler = resolveHandler;
        this.properties = properties;
        this.watermark = new Date(System.currentTimeMillis() - properties.getSourceLookbackSeconds() * 1000L);
    }

    public void tick() {
        scanNew();
        scanRecovered();
    }

    // ==================== ① 新增扫描（EXC_CREATED 语义）====================

    private void scanNew() {
        Date from = watermark;
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT " + cn.geelato.alert.engine.SourceSnapshots.SINGLE_COLUMNS
                        + ", tenant_code, create_at "
                        + "FROM platform_exception WHERE create_at >= ? AND del_status = 0 "
                        + "ORDER BY create_at ASC LIMIT " + Math.max(1, properties.getSourceBatchSize()),
                new Object[]{from});
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Map<String, List<FmsAlertRule>> rulesByTenant = new HashMap<>();
        Date maxCreateAt = from;
        int pipelined = 0;
        for (Map<String, Object> row : rows) {
            String id = str(pick(row, "id"));
            String tenantCode = str(pick(row, "tenant_code", "tenantCode"));
            String ruleCode = str(pick(row, "rule_code", "ruleCode"));
            Date createAt = toDate(pick(row, "create_at", "createAt"));
            if (createAt != null && createAt.after(maxCreateAt)) {
                maxCreateAt = createAt;
            }
            List<FmsAlertRule> rules = rulesByTenant.computeIfAbsent(
                    tenantCode == null ? "" : tenantCode, matcher::loadEventRules);
            for (FmsAlertRule rule : rules) {
                if (!AlertMatcher.match(rule, "EXC_CREATED", ruleCode) || alreadyPipelined(rule, id)) {
                    continue;
                }
                AlertContextExecutor.withContext(rule.getTenantCode(), () -> {
                    grouper.accept(rule, "EVENT", "EXC_CREATED", id, row);
                    return null;
                });
                pipelined++;
            }
        }
        watermark = maxCreateAt;
        if (pipelined > 0) {
            log.info("源扫描新增入流水线 {} 条（水位线推进至 {}）", pipelined, maxCreateAt);
        }
    }

    /** 该异常在此规则下已进过流水线（含 RESOLVE/REPEAT 记录；LIKE 粗筛 event_ids）。 */
    private boolean alreadyPipelined(FmsAlertRule rule, String exceptionId) {
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT id FROM platform_alert WHERE alert_code = ? AND tenant_code = ? AND del_status = 0 "
                        + "AND event_ids LIKE ? LIMIT 1",
                new Object[]{rule.getAlertCode(), rule.getTenantCode(), "%\"" + exceptionId + "\"%"});
        return rows != null && !rows.isEmpty();
    }

    // ==================== ② 恢复扫描（EXC_RECOVERED 语义）====================

    private void scanRecovered() {
        scanGroupClosure();
        scanIndependentResolve();
    }

    /** AGGREGATE 在途组闭环复查：全部实例终态 → 释放组 + 按需恢复通知（幂等：释放后查不到在途组）。 */
    private void scanGroupClosure() {
        List<Map<String, Object>> ruleRows = dao.nativeQueryForMapList(
                "SELECT * FROM platform_alert_rule WHERE enable_status = 1 AND del_status = 0 "
                        + "AND aggregate_mode = 'AGGREGATE' AND trigger_mode IN ('EVENT','BOTH')",
                new Object[]{});
        if (ruleRows == null) {
            return;
        }
        for (Map<String, Object> ruleRow : ruleRows) {
            FmsAlertRule rule = RowMappers.toRule(ruleRow);
            try {
                List<String> ids = activeGroupIds(rule);
                if (!ids.isEmpty()) {
                    // 组末实例 = 最近并入者；onRecovered 内部自带全终态判定与幂等释放
                    String lastId = ids.get(ids.size() - 1);
                    AlertContextExecutor.withContext(rule.getTenantCode(),
                            () -> resolveHandler.onRecovered(rule, lastId));
                }
            } catch (Exception e) {
                log.warn("组闭环复查异常 rule={}: {}", rule.getAlertCode(), e.getMessage());
            }
        }
    }

    /** INDEPENDENT + resolve_notify：近窗已告警异常终态且未发过 RESOLVE → 落恢复通知记录。 */
    private void scanIndependentResolve() {
        List<Map<String, Object>> ruleRows = dao.nativeQueryForMapList(
                "SELECT * FROM platform_alert_rule WHERE enable_status = 1 AND del_status = 0 "
                        + "AND aggregate_mode = 'INDEPENDENT' AND resolve_notify = 1 "
                        + "AND trigger_mode IN ('EVENT','BOTH')",
                new Object[]{});
        if (ruleRows == null) {
            return;
        }
        Date windowStart = new Date(System.currentTimeMillis() - RESOLVE_WINDOW_DAYS * 86_400_000L);
        for (Map<String, Object> ruleRow : ruleRows) {
            FmsAlertRule rule = RowMappers.toRule(ruleRow);
            try {
                Set<String> candidates = recentEventIds(rule, windowStart);
                if (candidates.isEmpty()) {
                    continue;
                }
                Set<String> resolvedNotified = resolvedNotifiedIds(rule);
                for (List<String> chunk : chunks(candidates)) {
                    resolveChunk(rule, chunk, resolvedNotified);
                }
            } catch (Exception e) {
                log.warn("INDEPENDENT 恢复复查异常 rule={}: {}", rule.getAlertCode(), e.getMessage());
            }
        }
    }

    private void resolveChunk(FmsAlertRule rule, List<String> ids, Set<String> resolvedNotified) {
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT " + cn.geelato.alert.engine.SourceSnapshots.SINGLE_COLUMNS
                        + " FROM platform_exception WHERE id IN (" + placeholders(ids.size())
                        + ") AND del_status = 0", ids.toArray());
        if (rows == null) {
            return;
        }
        for (Map<String, Object> row : rows) {
            String id = str(pick(row, "id"));
            String status = str(pick(row, "status"));
            String ruleCode = str(pick(row, "rule_code", "ruleCode"));
            if (!"RESOLVED".equals(status) && !"IGNORED".equals(status)) {
                continue;
            }
            if (resolvedNotified.contains(id) || !AlertMatcher.match(rule, "EXC_RECOVERED", ruleCode)) {
                continue;
            }
            AlertContextExecutor.withContext(rule.getTenantCode(), () -> {
                grouper.acceptResolve(rule, row);
                return null;
            });
            log.info("INDEPENDENT 恢复通知落记录 rule={} exception={}", rule.getAlertCode(), id);
        }
    }

    /** 规则在途组代表的 event_ids（active_group_key 非空最新一条）。 */
    private List<String> activeGroupIds(FmsAlertRule rule) {
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT event_ids FROM platform_alert WHERE alert_code = ? AND tenant_code = ? "
                        + "AND active_group_key IS NOT NULL AND del_status = 0 ORDER BY create_at DESC LIMIT 1",
                new Object[]{rule.getAlertCode(), rule.getTenantCode()});
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return AlertGrouper.parseIds(str(pick(rows.get(0), "event_ids", "eventIds")));
    }

    /** 近窗 EVENT 记录的异常 id 集（INDEPENDENT 每条单 id）。 */
    private Set<String> recentEventIds(FmsAlertRule rule, Date windowStart) {
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT event_ids FROM platform_alert WHERE alert_code = ? AND tenant_code = ? "
                        + "AND trigger_type = 'EVENT' AND del_status = 0 AND create_at >= ?",
                new Object[]{rule.getAlertCode(), rule.getTenantCode(), windowStart});
        Set<String> ids = new LinkedHashSet<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                ids.addAll(AlertGrouper.parseIds(str(pick(row, "event_ids", "eventIds"))));
            }
        }
        return ids;
    }

    /** 规则已发过 RESOLVE 通知的异常 id 集。 */
    private Set<String> resolvedNotifiedIds(FmsAlertRule rule) {
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT event_ids FROM platform_alert WHERE alert_code = ? AND tenant_code = ? "
                        + "AND trigger_type = 'RESOLVE' AND del_status = 0",
                new Object[]{rule.getAlertCode(), rule.getTenantCode()});
        Set<String> ids = new LinkedHashSet<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                ids.addAll(AlertGrouper.parseIds(str(pick(row, "event_ids", "eventIds"))));
            }
        }
        return ids;
    }

    // ==================== 内部辅助 ====================

    private static List<List<String>> chunks(Set<String> ids) {
        List<List<String>> out = new ArrayList<>();
        List<String> current = new ArrayList<>(IN_CHUNK);
        for (String id : ids) {
            current.add(id);
            if (current.size() >= IN_CHUNK) {
                out.add(current);
                current = new ArrayList<>(IN_CHUNK);
            }
        }
        if (!current.isEmpty()) {
            out.add(current);
        }
        return out;
    }

    private static String placeholders(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(i == 0 ? "?" : ",?");
        }
        return sb.toString();
    }

    private static Object pick(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        return null;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
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
}
