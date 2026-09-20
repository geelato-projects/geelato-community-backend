package cn.geelato.web.platform.srv.ormhook.service;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.orm.event.DeleteEventContext;
import cn.geelato.core.orm.event.EventExecutorFactory;
import cn.geelato.core.orm.event.SaveEventContext;
import cn.geelato.meta.OrmHook;
import cn.geelato.meta.OrmHookLog;
import cn.geelato.utils.UIDGenerator;
import cn.geelato.web.platform.srv.ormhook.enums.HookLogStatusEnum;
import cn.geelato.web.platform.srv.ormhook.enums.OrmHookEventEnum;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 钩子分发服务：afterCommit 回调的唯一落点。
 * <p>
 * <b>提交路径开销约束</b>：afterCommit 在提交线程上同步执行，本服务在此线程上只做
 * "构造内存快照 + 提交异步任务"（一次 map 查找 + 快照拷贝 + 任务提交），<b>绝无 DB/脚本执行</b>。
 * <p>
 * <b>事件路由</b>：保存事件按 opType 区分——insert 命中 insert 规则、update（含逻辑删除）
 * 命中 update 规则；删除事件命中 delete 规则。
 * <p>
 * <b>饱和保护</b>：分发池为有界队列 + CallerRunsPolicy；极端饱和下任务回落到提交线程执行时，
 * 任务只写发件箱行（一次索引 INSERT，毫秒级）并登记 1s 后由重试定时器拾取——立即执行永远
 * 只发生在 {@code orm-hook-*} 池线程上，业务提交线程最坏情况也只是一次 INSERT，永不执行脚本。
 * <p>
 * <b>已知崩溃窗口</b>：事务提交后、异步任务写发件箱前进程崩溃，则该次触发丢失——
 * 附加特性的可接受代价（如需零丢失，二期可评估业务事务内写 outbox 的原子方案）。
 *
 * @author geelato
 */
@Service
@Slf4j
public class HookDispatchService {

    private static final String TABLE = "platform_orm_hook_log";
    private static final String POOL_THREAD_PREFIX = "orm-hook-";
    private static final String INSERT_SQL = "INSERT INTO " + TABLE
            + " (id, hook_id, event_id, entity_name, event_type, op_type, action_type, payload_json, "
            + "status, retry_count, tenant_code, del_status, create_at, creator, update_at, updater) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 0, ?, ?, ?, ?)";

    private final OrmHookRegistry registry;
    private final OrmHookLogProcessor processor;
    private final OrmHookProperties properties;
    private final Dao dao;

    private ExecutorService dispatchExecutor;

    @Autowired
    public HookDispatchService(OrmHookRegistry registry,
                               OrmHookLogProcessor processor,
                               OrmHookProperties properties,
                               @Qualifier("primaryDao") Dao dao) {
        this.registry = registry;
        this.processor = processor;
        this.properties = properties;
        this.dao = dao;
    }

    @PostConstruct
    public void initExecutor() {
        dispatchExecutor = EventExecutorFactory.create(POOL_THREAD_PREFIX,
                properties.getDispatchPoolSize(), properties.getDispatchQueueCapacity());
    }

    @PreDestroy
    public void shutdownExecutor() {
        if (dispatchExecutor != null) {
            dispatchExecutor.shutdown();
            try {
                if (!dispatchExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    dispatchExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                dispatchExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /** 保存（insert/update）事务提交后触发：按 opType 路由到对应事件规则。 */
    public void dispatchSaveCommit(SaveEventContext ctx) {
        OrmHookEventEnum event = resolveSaveEvent(ctx);
        if (event == null) {
            return;
        }
        String entityName = ctx.getCommand() != null ? ctx.getCommand().getEntityName() : null;
        if (entityName == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", ctx.getEventId());
        payload.put("entityName", entityName);
        payload.put("tableName", resolveTableName(entityName));
        payload.put("event", event.value());
        payload.put("opType", ctx.getOperType() != null ? ctx.getOperType().name() : event.value());
        Map<String, Object> values = ctx.getResultValueMap() != null
                ? ctx.getResultValueMap()
                : (ctx.getCommand() != null ? ctx.getCommand().getValueMap() : null);
        payload.put("values", values != null ? new LinkedHashMap<>(values) : Map.of());
        payload.put("session", snapshotSession(ctx.getSessionCtx()));
        payload.put("firedAt", new Date());
        dispatch(entityName, event, payload);
    }

    /** 物理删除事务提交后触发。 */
    public void dispatchDeleteCommit(DeleteEventContext ctx) {
        String entityName = ctx.getCommand() != null ? ctx.getCommand().getEntityName() : null;
        if (entityName == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", ctx.getEventId());
        payload.put("entityName", entityName);
        payload.put("tableName", resolveTableName(entityName));
        payload.put("event", OrmHookEventEnum.DELETE.value());
        payload.put("opType", "Delete");
        Map<String, Object> values = ctx.getCommand() != null ? ctx.getCommand().getValueMap() : null;
        payload.put("values", values != null ? new LinkedHashMap<>(values) : Map.of());
        payload.put("affectedRows", ctx.getAffectedRows());
        payload.put("session", snapshotSession(ctx.getSessionCtx()));
        payload.put("firedAt", new Date());
        dispatch(entityName, OrmHookEventEnum.DELETE, payload);
    }

    /** 保存事件解析：Insert→insert 规则；Update（含逻辑删除）→update 规则；无法判定返回 null。 */
    private OrmHookEventEnum resolveSaveEvent(SaveEventContext ctx) {
        if (ctx.isInsert()) {
            return OrmHookEventEnum.INSERT;
        }
        if (ctx.isUpdate()) {
            return OrmHookEventEnum.UPDATE;
        }
        return null;
    }

    /** 提交线程仅做：规则查找 + 任务提交。任务内写发件箱行并立即尝试执行（仅池线程）。 */
    private void dispatch(String entityName, OrmHookEventEnum event, Map<String, Object> payload) {
        List<OrmHook> rules = registry.getRules(entityName, event);
        if (rules.isEmpty()) {
            return;
        }
        for (OrmHook rule : rules) {
            dispatchExecutor.submit(() -> {
                try {
                    OrmHookLog row = insertLogRow(rule, payload);
                    if (inPoolThread()) {
                        // 池线程：立即尝试执行（低延迟）；失败靠内存定时退避重试
                        processor.processOne(row);
                    } else {
                        // CallerRuns 回落到提交线程：只保证发件箱行落库，1s 后由重试定时器拾取（不依赖扫描）
                        processor.scheduleRetry(row.getId(), 1000);
                        log.debug("ORM Hook 分发池饱和，hookId={} 的发件箱行已写入并登记 1s 后拾取", rule.getId());
                    }
                } catch (Exception e) {
                    // 分发失败不影响任何业务语义；若 INSERT 前失败即该次触发丢失（已知崩溃窗口同源）
                    log.error("ORM Hook 分发失败 hookId={}, entity={}: {}", rule.getId(), entityName, e.getMessage(), e);
                }
            });
        }
    }

    private OrmHookLog insertLogRow(OrmHook rule, Map<String, Object> payload) {
        String payloadJson = JSON.toJSONString(payload);
        Map<String, String> session = sessionOf(payload);
        String operator = session != null && session.get("userId") != null ? session.get("userId") : "system";
        Date now = new Date();
        String id = String.valueOf(UIDGenerator.generate());
        dao.getJdbcTemplate().update(INSERT_SQL,
                id, rule.getId(), payload.get("eventId"), rule.getEntityName(), rule.getEventType(),
                payload.get("opType"), rule.getActionType(), payloadJson,
                HookLogStatusEnum.READY.value(), rule.getTenantCode(), now, operator, now, operator);
        OrmHookLog row = new OrmHookLog();
        row.setId(id);
        row.setHookId(rule.getId());
        row.setEventId((String) payload.get("eventId"));
        row.setEntityName(rule.getEntityName());
        row.setEventType(rule.getEventType());
        row.setOpType(payload.get("opType") != null ? String.valueOf(payload.get("opType")) : null);
        row.setActionType(rule.getActionType());
        row.setPayloadJson(payloadJson);
        row.setStatus(HookLogStatusEnum.READY.value());
        row.setRetryCount(0);
        return row;
    }

    /** 立即执行只允许发生在分发池线程（饱和回落时让渡给调度器）。 */
    private boolean inPoolThread() {
        return Thread.currentThread().getName().startsWith(POOL_THREAD_PREFIX);
    }

    private Map<String, String> snapshotSession(SessionCtx sessionCtx) {
        if (sessionCtx == null || sessionCtx.isEmpty()) {
            return Map.of();
        }
        return new LinkedHashMap<>(sessionCtx);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> sessionOf(Map<String, Object> payload) {
        Object session = payload.get("session");
        return session instanceof Map ? (Map<String, String>) session : null;
    }

    private String resolveTableName(String entityName) {
        try {
            EntityMeta em = MetaManager.singleInstance().getByEntityName(entityName);
            return em != null ? em.getTableName() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
