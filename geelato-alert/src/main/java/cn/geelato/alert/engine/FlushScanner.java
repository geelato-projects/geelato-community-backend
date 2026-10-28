package cn.geelato.alert.engine;

import cn.geelato.alert.engine.deliver.AlertRoute;
import cn.geelato.alert.engine.render.AlertRenderer;
import cn.geelato.alert.engine.silence.SilenceChecker;
import cn.geelato.core.orm.Dao;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 冲刷扫描（P-A2/P-A5 ③④收口）：flush_at 到期的 PENDING → 静默判定（SILENCED 终态）→
 * 渲染（default 变体快照）→ CAS 转 READY。渲染失败退避重试（update_at + backoff*2^retry），
 * 超 maxRetry 转 FAIL。宕机恢复天然覆盖（flush_at 过期即扫）。
 */
@Slf4j
public class FlushScanner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Dao dao;
    private final AlertRenderer renderer;
    private final int batchSize;
    private final int maxRetry;
    private final long backoffBaseSeconds;

    public FlushScanner(@Qualifier("dynamicDao") Dao dao, AlertRenderer renderer,
                        int batchSize, int maxRetry, long backoffBaseSeconds) {
        this.dao = dao;
        this.renderer = renderer;
        this.batchSize = Math.max(1, batchSize);
        this.maxRetry = Math.max(1, maxRetry);
        this.backoffBaseSeconds = Math.max(1, backoffBaseSeconds);
    }

    /** 单轮扫描（由调度器 tick 调用）。 */
    public void tick() {
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT * FROM platform_alert WHERE status = 'PENDING' AND flush_at <= NOW() "
                        + "AND del_status = 0 ORDER BY flush_at ASC LIMIT " + batchSize,
                new Object[]{});
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Date now = new Date();
        for (Map<String, Object> row : rows) {
            try {
                process(RowMappers.toAlert(row), now);
            } catch (Exception e) {
                log.warn("冲刷处理异常 record={}: {}", row.get("id"), e.getMessage(), e);
            }
        }
    }

    private void process(cn.geelato.alert.entity.FmsAlert record, Date now) {
        if (record.getRetryCount() > 0 && !backoffDue(record, now)) {
            return;
        }
        cn.geelato.alert.entity.FmsAlertRule rule = loadRule(record);
        if (rule == null) {
            markFail(record, "告警规则不存在或已删除: " + record.getAlertCode(), now);
            return;
        }
        if (rule.getEnableStatus() != 1) {
            markFail(record, "告警规则已停用: " + record.getAlertCode(), now);
            return;
        }
        // ③ 静默判定（渲染前，命中即终态不渲染；源维度取自告警快照 source_json 首条）
        Map<String, Object> firstSource;
        try {
            firstSource = firstSourceOf(record);
        } catch (IllegalStateException e) {
            markFail(record, e.getMessage(), now);
            return;
        }
        String silenceId = SilenceChecker.check(loadActiveSilences(record.getTenantCode()),
                record.getAlertCode(),
                str(pick(firstSource, "ruleCode")),
                str(pick(firstSource, "customerId")),
                record.getTriggerType(), now);
        if (silenceId != null) {
            dao.nativeExecute(
                    "UPDATE platform_alert SET status = 'SILENCED', silence_id = ?, update_at = ? "
                            + "WHERE id = ? AND status = 'PENDING'",
                    new Object[]{silenceId, now, record.getId()});
            return;
        }
        // ④ 渲染（schedule 形态从 stats_json 恢复 dataset）
        Map<String, Object> extraCtx = null;
        if ("SCHEDULE".equals(record.getTriggerType()) && record.getStatsJson() != null) {
            extraCtx = Map.of("dataset", parseJsonOrEmpty(record.getStatsJson()));
        }
        AlertRenderer.Snapshot snapshot;
        try {
            snapshot = renderer.renderSnapshot(record, rule, extraCtx);
        } catch (Exception e) {
            onRenderFail(record, e.getMessage(), now);
            return;
        }
        String channels = writeChannels(AlertRoute.channels(rule.getRouteJson()));
        if (channels == null) {
            onRenderFail(record, "规则通道路由为空 route.default.channels: " + rule.getAlertCode(), now);
            return;
        }
        // ⑤ 转 READY（CAS：期间被并发变更则放弃本条）
        int rows = dao.nativeExecute(
                "UPDATE platform_alert SET status = 'READY', rendered_title = ?, rendered_content = ?, "
                        + "stats_json = ?, channels = ?, batch_no = ?, update_at = ? "
                        + "WHERE id = ? AND status = 'PENDING'",
                new Object[]{truncate(snapshot.getTitle(), 120), snapshot.getContent(),
                        snapshot.getStats() == null ? null : writeJson(snapshot.getStats()), channels,
                        EngineConsts.nowBatchNo("FL"), now, record.getId()});
        if (rows == 0) {
            log.debug("冲刷 CAS 失败（状态已变更）record={}", record.getId());
        }
    }

    // ==================== 内部辅助 ====================

    private boolean backoffDue(cn.geelato.alert.entity.FmsAlert record, Date now) {
        long backoffMs = backoffBaseSeconds * 1000L * (1L << Math.min(record.getRetryCount(), 10));
        Date due = new Date(record.getUpdateAt() == null ? 0 : record.getUpdateAt().getTime() + backoffMs);
        return !now.before(due);
    }

    private void onRenderFail(cn.geelato.alert.entity.FmsAlert record, String error, Date now) {
        int nextRetry = record.getRetryCount() + 1;
        if (nextRetry >= maxRetry) {
            markFail(record, error, now);
            return;
        }
        dao.nativeExecute(
                "UPDATE platform_alert SET retry_count = ?, error_message = ?, update_at = ? "
                        + "WHERE id = ? AND status = 'PENDING'",
                new Object[]{nextRetry, truncate(error, 512), now, record.getId()});
    }

    private void markFail(cn.geelato.alert.entity.FmsAlert record, String error, Date now) {
        dao.nativeExecute(
                "UPDATE platform_alert SET status = 'FAIL', error_message = ?, update_at = ? WHERE id = ?",
                new Object[]{truncate(error, 512), now, record.getId()});
        log.warn("告警记录转 FAIL record={} rule={} error={}", record.getId(), record.getAlertCode(), error);
    }

    private cn.geelato.alert.entity.FmsAlertRule loadRule(cn.geelato.alert.entity.FmsAlert record) {
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT * FROM platform_alert_rule WHERE alert_code = ? AND tenant_code = ? AND del_status = 0",
                new Object[]{record.getAlertCode(), record.getTenantCode()});
        return rows == null || rows.isEmpty() ? null : RowMappers.toRule(rows.get(0));
    }

    /** 告警快照首条源实例（single=对象自身；group=清单首条；schedule 无源返回 null）。 */
    private Map<String, Object> firstSourceOf(cn.geelato.alert.entity.FmsAlert record) {
        try {
            return cn.geelato.alert.engine.SourceSnapshots.parseItem(record.getSourceJson());
        } catch (IllegalStateException e) {
            throw new IllegalStateException("source_json 解析失败: record=" + record.getId() + ", " + e.getMessage());
        }
    }

    private List<cn.geelato.alert.entity.FmsAlertSilence> loadActiveSilences(String tenantCode) {
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT * FROM platform_alert_silence WHERE status = 'ACTIVE' AND del_status = 0 AND tenant_code = ?",
                new Object[]{tenantCode});
        List<cn.geelato.alert.entity.FmsAlertSilence> list = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                list.add(RowMappers.toSilence(row));
            }
        }
        return list;
    }

    private static String writeChannels(List<String> channels) {
        if (channels == null || channels.isEmpty()) {
            return null;
        }
        return writeJson(channels);
    }

    private static Object parseJsonOrEmpty(String json) {
        try {
            Object v = MAPPER.readValue(json, Object.class);
            return v == null ? Map.of() : v;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }

    private static Object pick(Map<String, Object> row, String... keys) {
        if (row == null) {
            return null;
        }
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
}
