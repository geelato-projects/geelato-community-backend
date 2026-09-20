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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

/**
 * 钩子发件箱行处理器：立即执行路径与调度器扫描路径共用的唯一执行事实来源
 * （对齐通知 outbox 的 CAS 抢占 + 结果回写模式，表 {@code platform_orm_hook_log}）。
 * <p>
 * 单行语义：CAS 抢占 ready→processing → 按 hook_id 回读<b>当前</b>钩子配置
 * （配置已删除/禁用/损坏 → 死信，不盲目重试）→ 按当前 actionType 路由执行器执行
 * （深度守卫包裹，防脚本内写回直接递归）→ 成功置 success / 失败按指数退避置回 ready、
 * 达上限置 dead。任何异常都被收敛为行状态，<b>绝不影响业务链路</b>。
 *
 * @author geelato
 */
@Component
@Slf4j
public class OrmHookLogProcessor {

    private static final String LOG_TABLE = "platform_orm_hook_log";
    private static final String HOOK_TABLE = "platform_orm_hook";

    private final Dao dao;
    private final OrmHookActionManager actionManager;
    private final OrmHookProperties properties;

    @Autowired
    public OrmHookLogProcessor(@Qualifier("primaryDao") Dao dao,
                                OrmHookActionManager actionManager,
                                OrmHookProperties properties) {
        this.dao = dao;
        this.actionManager = actionManager;
        this.properties = properties;
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

    /** 回读当前钩子配置：存在、未删且启用才执行（列别名驼峰供 fastjson 映射）。 */
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

    /** 失败：未达上限则重试（ready + 指数退避 max(30s, 2^retry × 60s)），达上限死信。 */
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
