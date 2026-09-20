package cn.geelato.web.platform.srv.ormhook.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.meta.OrmHook;
import cn.geelato.meta.OrmHookLog;
import cn.geelato.web.platform.srv.ormhook.enums.HookLogStatusEnum;
import cn.geelato.web.platform.srv.ormhook.spi.OrmHookActionExecutor;
import cn.geelato.web.platform.srv.ormhook.spi.OrmHookActionManager;
import cn.geelato.web.platform.srv.ormhook.spi.OrmHookActionResult;
import cn.geelato.web.platform.srv.ormhook.spi.OrmHookDepthHolder;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 钩子发件箱行处理器：执行的唯一事实来源（立即执行、内存定时重试、兜底扫描共用）。
 * <p>
 * <b>去轮询设计</b>：健康路径完全事件驱动（afterCommit 写行后立即执行），<b>无固定扫描</b>：
 * <ul>
 *   <li>单行执行：CAS 抢占 ready→processing → 按 hook_id 回读<b>当前</b>钩子配置（配置已删/禁用→死信）
 *       → 按当前 actionType 路由执行器执行（深度守卫包裹防脚本写回递归）
 *       → 成功置 success / 失败退避后经 {@link #scheduleRetry} 内存精确定时重试、达上限死信</li>
 *   <li>重试计时：退避时长在失败时已知，用单线程守护定时器精确调度，不等扫描；
 *       行状态照写 ready+next_retry_at（可观测、重启后可被 {@link #sweep} 恢复）</li>
 *   <li>{@link #sweep} 兜底扫描（由调度器启动时与低频周期触发）：恢复进程崩溃遗留
 *       （重启丢失的内存定时、卡死的 processing）与他实例故障行——CAS 抢占保证多实例安全</li>
 * </ul>
 * 任何异常都被收敛为行状态，<b>绝不影响业务链路</b>。
 *
 * @author geelato
 */
@Component
@Slf4j
public class OrmHookLogProcessor {

    /** processing 超时回收阈值（分钟）：claim 后超此时长未完成视为执行实例崩溃残留 */
    private static final int STALE_PROCESSING_MINUTES = 10;

    private static final String LOG_TABLE = "platform_orm_hook_log";
    private static final String HOOK_TABLE = "platform_orm_hook";

    private final Dao dao;
    private final OrmHookActionManager actionManager;
    private final OrmHookProperties properties;

    /** 重试定时器：单线程守护，健康路径零 DB 负载的精确定时来源。 */
    private ScheduledExecutorService retryTimer;

    @Autowired
    public OrmHookLogProcessor(@Qualifier("primaryDao") Dao dao,
                                OrmHookActionManager actionManager,
                                OrmHookProperties properties) {
        this.dao = dao;
        this.actionManager = actionManager;
        this.properties = properties;
    }

    @PostConstruct
    public void initRetryTimer() {
        retryTimer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "orm-hook-retry");
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    public void shutdownRetryTimer() {
        if (retryTimer != null) {
            retryTimer.shutdown();
            try {
                if (!retryTimer.awaitTermination(5, TimeUnit.SECONDS)) {
                    retryTimer.shutdownNow();
                }
            } catch (InterruptedException e) {
                retryTimer.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 内存定时拾取：delayMs 后按 id 执行该行（行仍为 ready 且已到期才执行）。
     * 用于失败退避重试（精确计时）、饱和回退与死信重放的立即拾取——均不依赖扫描。
     */
    public void scheduleRetry(String logId, long delayMs) {
        if (logId == null || logId.isBlank() || retryTimer == null) {
            return;
        }
        retryTimer.schedule(() -> {
            try {
                processById(logId);
            } catch (Throwable t) {
                // 兜底：定时任务异常不能中断后续调度（schedule 遇异常会取消该任务）
                log.error("ORM Hook 定时重试异常 hookLogId={}: {}", logId, t.getMessage(), t);
            }
        }, Math.max(delayMs, 0), TimeUnit.MILLISECONDS);
    }

    /** 按 id 拾取一行：仍为 ready 且已到期才执行（防重放/状态变化后的过期拾取）。 */
    public void processById(String logId) {
        OrmHookLog row = fetchDueById(logId);
        if (row != null) {
            processOne(row);
        }
    }

    /** 兜底扫描：回收超时 processing → 取到期 ready → 逐行执行。由调度器启动时与低频周期触发。 */
    public void sweep() {
        reclaimStaleProcessing();
        List<OrmHookLog> ready = fetchReady();
        for (OrmHookLog row : ready) {
            try {
                processOne(row);
            } catch (Exception e) {
                log.error("处理 ORM Hook 发件箱行异常 id={}, hookId={}: {}", row.getId(), row.getHookId(), e.getMessage(), e);
            }
        }
    }

    /** 处理一行：抢占→回读配置→执行→回写。抢占失败（他实例已抢/行状态已变）静默让出。 */
    public void processOne(OrmHookLog row) {
        if (row == null || row.getId() == null) {
            return;
        }
        if (!claim(row.getId())) {
            return;
        }
        OrmHook rule = loadRule(row.getHookId());
        if (rule == null) {
            markDead(row, String.format("钩子配置不存在或已禁用/删除（hookId=%s），执行时按当前配置判定", row.getHookId()));
            return;
        }
        OrmHookActionExecutor executor = actionManager.getExecutor(rule.getActionType());
        if (executor == null) {
            markDead(row, "无可用动作执行器：" + rule.getActionType());
            return;
        }
        OrmHookActionResult result;
        try {
            Map<String, Object> payload = parsePayload(row.getPayloadJson());
            result = OrmHookDepthHolder.call(() -> executor.execute(rule, payload));
        } catch (Exception e) {
            log.error("ORM Hook 执行异常 hookLogId={}, hookId={}: {}", row.getId(), row.getHookId(), e.getMessage(), e);
            result = OrmHookActionResult.fail(e.getMessage());
        }
        if (result.isSuccess()) {
            markSuccess(row);
        } else {
            markForRetryOrDead(row, result.getErrorMessage());
        }
    }

    /** 回读当前钩子配置：存在、未删且启用才执行（列别名驼峰供 RowMapper）。 */
    private OrmHook loadRule(String hookId) {
        if (hookId == null || hookId.isBlank()) {
            return null;
        }
        try {
            return dao.getJdbcTemplate().queryForObject(
                    "SELECT id, title, entity_name AS entityName, event_type AS eventType, "
                            + "action_type AS actionType, script_content AS scriptContent, "
                            + "http_method AS httpMethod, http_url AS httpUrl, "
                            + "http_headers AS httpHeaders, http_body AS httpBody, enable_status AS enableStatus "
                            + "FROM " + HOOK_TABLE + " WHERE id = ? AND del_status = 0 AND enable_status = 1",
                    (rs, rowNum) -> {
                        OrmHook hook = new OrmHook();
                        hook.setId(rs.getString("id"));
                        hook.setTitle(rs.getString("title"));
                        hook.setEntityName(rs.getString("entityName"));
                        hook.setEventType(rs.getString("eventType"));
                        hook.setActionType(rs.getString("actionType"));
                        hook.setScriptContent(rs.getString("scriptContent"));
                        hook.setHttpMethod(rs.getString("httpMethod"));
                        hook.setHttpUrl(rs.getString("httpUrl"));
                        hook.setHttpHeaders(rs.getString("httpHeaders"));
                        hook.setHttpBody(rs.getString("httpBody"));
                        hook.setEnableStatus(rs.getInt("enableStatus"));
                        return hook;
                    }, hookId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }

    /** 按 id 取行：仅当 status=ready 且已到期（内存定时到期拾取与扫描共用的状态闸门）。 */
    private OrmHookLog fetchDueById(String logId) {
        List<OrmHookLog> rows = dao.getJdbcTemplate().query(
                "SELECT id, hook_id AS hookId, event_id AS eventId, entity_name AS entityName, "
                        + "event_type AS eventType, op_type AS opType, action_type AS actionType, "
                        + "payload_json AS payloadJson, status, retry_count AS retryCount "
                        + "FROM " + LOG_TABLE + " WHERE id = ? AND del_status = 0 AND status = ? "
                        + "AND (next_retry_at IS NULL OR next_retry_at <= ?)",
                (rs, rowNum) -> mapRow(rs), logId, HookLogStatusEnum.READY.value(), new Date());
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 回收超时的 processing 行：执行实例在 claim 后崩溃会使该行永卡 processing，
     * 阈值远大于单次动作时长，正常执行中行不会被误伤；即使误回收导致重复执行，动作方需自带幂等。
     */
    private void reclaimStaleProcessing() {
        int reclaimed = dao.getJdbcTemplate().update(
                "UPDATE " + LOG_TABLE + " SET status = ?, update_at = ? "
                        + "WHERE status = ? AND update_at < DATE_SUB(NOW(), INTERVAL " + STALE_PROCESSING_MINUTES + " MINUTE)",
                HookLogStatusEnum.READY.value(), new Date(), HookLogStatusEnum.PROCESSING.value());
        if (reclaimed > 0) {
            log.warn("回收超时 processing 的 ORM Hook 发件箱行 {} 条（疑似执行实例崩溃，已重置为 ready 待重试）", reclaimed);
        }
    }

    /** 取一批就绪项：status=ready 且到期，按 next_retry_at 升序，限制 batchSize；列显式别名为驼峰。 */
    private List<OrmHookLog> fetchReady() {
        String sql = "SELECT id, hook_id AS hookId, event_id AS eventId, entity_name AS entityName, "
                + "event_type AS eventType, op_type AS opType, action_type AS actionType, "
                + "payload_json AS payloadJson, status, retry_count AS retryCount, "
                + "next_retry_at AS nextRetryAt, error_msg AS errorMsg "
                + "FROM " + LOG_TABLE + " WHERE del_status = 0 AND status = ? "
                + "AND (next_retry_at IS NULL OR next_retry_at <= ?) "
                + "ORDER BY next_retry_at ASC, create_at ASC LIMIT ?";
        List<OrmHookLog> list = new ArrayList<>();
        for (OrmHookLog row : dao.getJdbcTemplate().query(sql,
                (rs, rowNum) -> mapRow(rs), HookLogStatusEnum.READY.value(), new Date(), properties.getBatchSize())) {
            list.add(row);
        }
        return list;
    }

    private OrmHookLog mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        OrmHookLog row = new OrmHookLog();
        row.setId(rs.getString("id"));
        row.setHookId(rs.getString("hookId"));
        row.setEventId(rs.getString("eventId"));
        row.setEntityName(rs.getString("entityName"));
        row.setEventType(rs.getString("eventType"));
        row.setOpType(rs.getString("opType"));
        row.setActionType(rs.getString("actionType"));
        row.setPayloadJson(rs.getString("payloadJson"));
        row.setStatus(rs.getString("status"));
        row.setRetryCount(rs.getInt("retryCount"));
        row.setNextRetryAt(rs.getTimestamp("nextRetryAt"));
        row.setErrorMsg(rs.getString("errorMsg"));
        return row;
    }

    private Map<String, Object> parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }
        return JSON.parseObject(payloadJson);
    }

    /** CAS 抢占：UPDATE status=processing WHERE id=? AND status=ready，返回是否抢占成功（多实例安全）。 */
    private boolean claim(String id) {
        int n = dao.getJdbcTemplate().update(
                "UPDATE " + LOG_TABLE + " SET status = ?, update_at = ? WHERE id = ? AND status = ?",
                HookLogStatusEnum.PROCESSING.value(), new Date(), id, HookLogStatusEnum.READY.value());
        return n > 0;
    }

    private void markSuccess(OrmHookLog row) {
        dao.getJdbcTemplate().update(
                "UPDATE " + LOG_TABLE + " SET status = ?, update_at = ? WHERE id = ?",
                HookLogStatusEnum.SUCCESS.value(), new Date(), row.getId());
        if (log.isDebugEnabled()) {
            log.debug("ORM Hook 执行成功 hookLogId={}, hookId={}, entity={}", row.getId(), row.getHookId(), row.getEntityName());
        }
    }

    private void markDead(OrmHookLog row, String reason) {
        dao.getJdbcTemplate().update(
                "UPDATE " + LOG_TABLE + " SET status = ?, error_msg = ?, update_at = ? WHERE id = ?",
                HookLogStatusEnum.DEAD.value(), truncate(reason, 500), new Date(), row.getId());
        log.error("ORM Hook 进入死信 hookLogId={}, hookId={}, entity={}, reason={}",
                row.getId(), row.getHookId(), row.getEntityName(), reason);
    }

    /** 失败：未达上限则退避重试（ready+next_retry_at 落库可观测，内存精确定时到点拾取），达上限死信。 */
    private void markForRetryOrDead(OrmHookLog row, String reason) {
        int newRetry = row.getRetryCount() + 1;
        if (newRetry >= properties.getMaxRetryCount()) {
            markDead(row, "达到最大重试次数(" + properties.getMaxRetryCount() + ")：" + reason);
            return;
        }
        long backoffMs = Math.max(30_000L, (1L << newRetry) * 60_000L);
        Date nextRetry = new Date(System.currentTimeMillis() + backoffMs);
        dao.getJdbcTemplate().update(
                "UPDATE " + LOG_TABLE + " SET status = ?, retry_count = ?, next_retry_at = ?, error_msg = ?, update_at = ? WHERE id = ?",
                HookLogStatusEnum.READY.value(), newRetry, nextRetry, truncate(reason, 500), new Date(), row.getId());
        // 内存精确定时：到点直接拾取，不等扫描（重启丢失由启动 sweep 恢复）
        scheduleRetry(row.getId(), backoffMs);
        log.warn("ORM Hook 执行失败将重试({}/{}) hookLogId={}, hookId={}, entity={}, nextRetryAt={}, reason={}",
                newRetry, properties.getMaxRetryCount(), row.getId(), row.getHookId(), row.getEntityName(), nextRetry, reason);
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
