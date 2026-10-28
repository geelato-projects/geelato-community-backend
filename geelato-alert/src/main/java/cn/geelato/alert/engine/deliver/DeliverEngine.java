package cn.geelato.alert.engine.deliver;

import cn.geelato.alert.boot.AlertEngineProperties;
import cn.geelato.alert.engine.EngineConsts;
import cn.geelato.alert.engine.RowMappers;
import cn.geelato.alert.engine.receiver.AlertReceiver;
import cn.geelato.alert.engine.receiver.AlertReceiverSolver;
import cn.geelato.alert.engine.receiver.AlertResolveCtx;
import cn.geelato.alert.engine.render.AlertRenderer;
import cn.geelato.alert.engine.silence.SilenceChecker;
import cn.geelato.alert.entity.FmsAlert;
import cn.geelato.alert.entity.FmsAlertRule;
import cn.geelato.alert.entity.FmsAlertSilence;
import cn.geelato.core.orm.Dao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 投递引擎（P-A6）：消费 READY → 静默终判 → 渲染兜底 → 接收人求解（首投快照）→
 * daily_limit → 按通道路由（wework→weixin_work_group）拆条 enqueue（bizKey={recordId}:{batchNo}:{channel}，
 * 幂等由 message idempotency_key 兜底）→ 回写 SENT/PARTIAL/FAIL。全渠道失败退避重试，超限转 FAIL 死信。
 */
@Slf4j
public class DeliverEngine {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Dao dao;
    private final MessageTransport transport;
    private final AlertRenderer renderer;
    private final AlertReceiverSolver receiverSolver;
    private final AlertEngineProperties properties;

    public DeliverEngine(@Qualifier("dynamicDao") Dao dao, MessageTransport transport,
                         AlertRenderer renderer, AlertReceiverSolver receiverSolver,
                         AlertEngineProperties properties) {
        this.dao = dao;
        this.transport = transport;
        this.renderer = renderer;
        this.receiverSolver = receiverSolver;
        this.properties = properties;
    }

    /** 单轮投递扫描（调度器 tick 调用）。 */
    public void deliver() {
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT * FROM platform_alert WHERE status = 'READY' AND del_status = 0 "
                        + "ORDER BY flush_at ASC LIMIT " + Math.max(1, properties.getDeliverBatchSize()),
                new Object[]{});
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Date now = new Date();
        for (Map<String, Object> row : rows) {
            FmsAlert record = RowMappers.toAlert(row);
            try {
                if (record.getRetryCount() > 0 && !backoffDue(record, now)) {
                    continue;
                }
                deliverOne(record, false);
            } catch (Exception e) {
                log.warn("投递处理异常 record={}: {}", record.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 单条投递（调度扫描与手动重发共用）。
     *
     * @param resendOnlySnapshot true=手动重发：渲染快照重投，不重渲染不重求解（快照缺失项仍兜底求解）
     */
    public void deliverOne(FmsAlert record, boolean resendOnlySnapshot) {
        Date now = new Date();
        FmsAlertRule rule = loadRule(record);
        if (rule == null) {
            markFail(record, "告警规则不存在或已删除: " + record.getAlertCode(), now);
            return;
        }
        // 静默终判（冲刷判定后窗口期内新建的静默在此拦截；RESOLVE 由 allowResolve 豁免）
        String silenceId = SilenceChecker.check(loadActiveSilences(record.getTenantCode()),
                record.getAlertCode(), null, firstCustomerId(record), record.getTriggerType(), now);
        if (silenceId != null) {
            dao.nativeExecute("UPDATE platform_alert SET status = 'SILENCED', silence_id = ?, update_at = ? WHERE id = ?",
                    new Object[]{silenceId, now, record.getId()});
            return;
        }
        // 渲染兜底（正常链路冲刷已渲染；admin 手动落库的 READY 无快照）
        if ((record.getRenderedTitle() == null || record.getRenderedTitle().isBlank())
                && !resendOnlySnapshot) {
            try {
                AlertRenderer.Snapshot snapshot = renderer.renderSnapshot(record, rule, null);
                record.setRenderedTitle(snapshot.getTitle());
                record.setRenderedContent(snapshot.getContent());
                dao.nativeExecute("UPDATE platform_alert SET rendered_title = ?, rendered_content = ?, update_at = ? "
                                + "WHERE id = ?",
                        new Object[]{snapshot.getTitle(), snapshot.getContent(), now, record.getId()});
            } catch (Exception e) {
                onDeliverFail(record, "渲染兜底失败: " + e.getMessage(), now);
                return;
            }
        }
        if (record.getRenderedContent() == null || record.getRenderedContent().isBlank()) {
            markFail(record, "渲染快照为空且重发不重渲染", now);
            return;
        }
        // 接收人（快照优先；首投求解并回填快照）
        List<AlertReceiver> receivers = resolveReceivers(record, rule);
        if (receivers.isEmpty()) {
            markFail(record, "接收人求解为空（extra 各选择器均无人，且未配置兜底角色）", now);
            return;
        }
        // 每日上限
        if (rule.getDailyLimit() != null && rule.getDailyLimit() > 0
                && countTodaySent(record) >= rule.getDailyLimit()) {
            markFail(record, "EXCEED_DAILY_LIMIT 当日发送已达上限 " + rule.getDailyLimit(), now);
            return;
        }
        // 拆条投递（每通道一条消息，receiver.list 全员）
        List<String> channels = channelsOf(record, rule);
        if (channels.isEmpty()) {
            markFail(record, "投递通道组为空: channels=" + record.getChannels(), now);
            return;
        }
        List<String> userIds = new ArrayList<>();
        Map<String, String> nameById = new LinkedHashMap<>();
        for (AlertReceiver r : receivers) {
            userIds.add(r.getId());
            nameById.put(r.getId(), r.getName());
        }
        String bizKeyPrefix = record.getId() + ":" + (record.getBatchNo() == null ? "-" : record.getBatchNo());
        Map<String, String> failures = new LinkedHashMap<>();
        for (String channel : channels) {
            try {
                String type = ChannelRouter.toMessageType(channel);
                String title = record.getRenderedTitle();
                String content = record.getRenderedContent();
                if (!resendOnlySnapshot) {
                    VariantScriptsHolder holder = renderChannelVariant(record, rule, channel);
                    if (holder != null) {
                        title = holder.title();
                        content = holder.content();
                    }
                }
                String bizKey = bizKeyPrefix + ":" + channel;
                transport.enqueue(EnqueuePayload.ofUserIds(type, title, content, bizKey, userIds));
            } catch (Exception e) {
                failures.put(channel, e.getMessage());
                log.warn("通道投递失败 record={} channel={}: {}", record.getId(), channel, e.getMessage());
            }
        }
        // 回写
        if (failures.isEmpty()) {
            dao.nativeExecute(
                    "UPDATE platform_alert SET status = 'SENT', biz_key = ?, receiver_ids = ?, receiver_names = ?, "
                            + "sent_at = ?, last_sent_at = ?, error_message = NULL, update_at = ? WHERE id = ?",
                    new Object[]{bizKeyPrefix, writeJson(userIds), writeJson(nameById), now, now, now, record.getId()});
            log.info("告警投递成功 record={} rule={} channels={} receivers={}",
                    record.getId(), record.getAlertCode(), channels, userIds.size());
        } else if (failures.size() < channels.size()) {
            dao.nativeExecute(
                    "UPDATE platform_alert SET status = 'PARTIAL', biz_key = ?, receiver_ids = ?, receiver_names = ?, "
                            + "last_sent_at = ?, error_message = ?, update_at = ? WHERE id = ?",
                    new Object[]{bizKeyPrefix, writeJson(userIds), writeJson(nameById), now,
                            truncate(describe(failures), 512), now, record.getId()});
        } else {
            onDeliverFail(record, describe(failures), now);
        }
    }

    // ==================== 渠道变体渲染（轻量持有） ====================

    private record VariantScriptsHolder(String title, String content) {
    }

    private VariantScriptsHolder renderChannelVariant(FmsAlert record, FmsAlertRule rule, String channel) {
        try {
            cn.geelato.alert.engine.render.VariantScripts.RenderResult r = renderer.renderChannel(record, rule, channel);
            if (r != null && r.getTitle() != null) {
                return new VariantScriptsHolder(r.getTitle(), r.getContent() == null
                        ? record.getRenderedContent() : r.getContent());
            }
        } catch (Exception e) {
            log.warn("渠道变体渲染失败，回退主快照 record={} channel={}: {}", record.getId(), channel, e.getMessage());
        }
        return null;
    }

    // ==================== 内部辅助 ====================

    private List<AlertReceiver> resolveReceivers(FmsAlert record, FmsAlertRule rule) {
        List<String> snapshotIds = parseIds(record.getReceiverIds());
        if (!snapshotIds.isEmpty()) {
            List<AlertReceiver> out = new ArrayList<>();
            for (String id : snapshotIds) {
                out.add(new AlertReceiver(id, id, null));
            }
            return out;
        }
        // 源维度参与人取自告警快照 source_json 首条（schedule 无源时为 null）
        Map<String, Object> first = firstSourceOf(record);
        AlertResolveCtx ctx = new AlertResolveCtx(
                str(pick(first, "participantId")),
                str(pick(first, "participantName")),
                str(pick(first, "participantOrg")));
        return receiverSolver.solve(rule.getReceiverJson(), ctx);
    }

    private long countTodaySent(FmsAlert record) {
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT COUNT(*) AS cnt FROM platform_alert WHERE alert_code = ? AND tenant_code = ? "
                        + "AND status IN ('SENT','PARTIAL') AND last_sent_at >= CURDATE()",
                new Object[]{record.getAlertCode(), record.getTenantCode()});
        if (rows != null && !rows.isEmpty()) {
            Object v = rows.get(0).containsKey("cnt") ? rows.get(0).get("cnt") : rows.get(0).get("CNT");
            if (v instanceof Number n) {
                return n.longValue();
            }
        }
        return 0;
    }

    private List<String> channelsOf(FmsAlert record, FmsAlertRule rule) {
        List<String> fromSnapshot = parseIds(record.getChannels());
        if (!fromSnapshot.isEmpty()) {
            return fromSnapshot;
        }
        return AlertRoute.channels(rule.getRouteJson());
    }

    private void onDeliverFail(FmsAlert record, String error, Date now) {
        int nextRetry = record.getRetryCount() + 1;
        if (nextRetry >= properties.getMaxRetry()) {
            markFail(record, error, now);
            return;
        }
        dao.nativeExecute(
                "UPDATE platform_alert SET retry_count = ?, error_message = ?, update_at = ? WHERE id = ?",
                new Object[]{nextRetry, truncate(error, 512), now, record.getId()});
    }

    private void markFail(FmsAlert record, String error, Date now) {
        dao.nativeExecute(
                "UPDATE platform_alert SET status = 'FAIL', error_message = ?, update_at = ? WHERE id = ?",
                new Object[]{truncate(error, 512), now, record.getId()});
        log.warn("告警投递转 FAIL record={} rule={} error={}", record.getId(), record.getAlertCode(), error);
    }

    private FmsAlertRule loadRule(FmsAlert record) {
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT * FROM platform_alert_rule WHERE alert_code = ? AND tenant_code = ? AND del_status = 0",
                new Object[]{record.getAlertCode(), record.getTenantCode()});
        return rows == null || rows.isEmpty() ? null : RowMappers.toRule(rows.get(0));
    }

    private List<FmsAlertSilence> loadActiveSilences(String tenantCode) {
        List<Map<String, Object>> rows = dao.nativeQueryForMapList(
                "SELECT * FROM platform_alert_silence WHERE status = 'ACTIVE' AND del_status = 0 AND tenant_code = ?",
                new Object[]{tenantCode});
        List<FmsAlertSilence> list = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                list.add(RowMappers.toSilence(row));
            }
        }
        return list;
    }

    /** 告警快照首条源实例（single=对象自身；group=清单首条；schedule 无源返回 null）。 */
    private Map<String, Object> firstSourceOf(FmsAlert record) {
        return cn.geelato.alert.engine.SourceSnapshots.parseItem(record.getSourceJson());
    }

    private String firstCustomerId(FmsAlert record) {
        Map<String, Object> first = firstSourceOf(record);
        return first == null ? null : str(pick(first, "customerId"));
    }

    private boolean backoffDue(FmsAlert record, Date now) {
        long backoffMs = properties.getBackoffBaseSeconds() * 1000L
                * (1L << Math.min(record.getRetryCount(), 10));
        Date due = new Date(record.getUpdateAt() == null ? 0 : record.getUpdateAt().getTime() + backoffMs);
        return !now.before(due);
    }

    private static String describe(Map<String, String> failures) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : failures.entrySet()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(e.getKey()).append(": ").append(e.getValue());
        }
        return sb.toString();
    }

    private static List<String> parseIds(String json) {
        List<String> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node.isArray()) {
                for (JsonNode n : node) {
                    String v = n.asText(null);
                    if (v != null && !v.isBlank()) {
                        out.add(v.trim());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return null;
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
