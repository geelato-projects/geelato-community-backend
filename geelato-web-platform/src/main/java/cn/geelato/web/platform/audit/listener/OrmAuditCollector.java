package cn.geelato.web.platform.audit.listener;

import cn.geelato.core.mql.command.CommandType;
import cn.geelato.core.mql.command.SaveCommand;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.orm.Dao;
import cn.geelato.web.platform.audit.boot.AuditLogProperties;
import cn.geelato.web.platform.audit.enums.AuditCaptureLayer;
import cn.geelato.web.platform.audit.enums.AuditOperType;
import cn.geelato.web.platform.audit.model.AuditFieldChange;
import cn.geelato.web.platform.audit.model.AuditLogRecord;
import cn.geelato.web.platform.audit.service.AuditBusinessNamer;
import cn.geelato.web.platform.audit.service.AuditContextProvider;
import cn.geelato.web.platform.audit.service.AuditDiffService;
import cn.geelato.web.platform.audit.service.AuditLogService;
import cn.geelato.web.platform.audit.context.AuditActorSnapshot;
import cn.geelato.web.platform.audit.context.AuditContext;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * ORM 监听器共享的审计收集逻辑：回查旧值、构建 diff、生成 AuditLog、提交后落库。
 *
 * <p>由 {@link AuditLogSaveEventListener} / {@link AuditLogDeleteEventListener} 调用，
 * 避免重复代码。该类是普通组件，但逻辑以静态/纯函数方式组织，无状态。
 */
@Slf4j
public abstract class OrmAuditCollector {

    /** 在 beforeSave 阶段处理：回查旧值（仅 update）、构建明细、登记提交后落库。 */
    public static void handleSave(Dao dao, SaveCommand command, EntityMeta em,
                                  AuditServices svc) {
        if (!shouldAudit(em, command.getEntityName(), svc.properties)) {
            return;
        }
        String entityName = command.getEntityName();
        CommandType ct = command.getCommandType();
        Map<String, Object> newValue = command.getValueMap();

        // 主键
        String pkValue = command.getPK();
        String targetId = pkValue;
        Class<?> entityClass = entityClass(em);

        Map<String, Object> before = null;
        if (ct == CommandType.Update && entityClass != null && pkValue != null) {
            // 回查旧值（删除/更新前数据还在库）
            try {
                String idField = idFieldName(em);
                List<Map<String, Object>> list = dao.queryForMapList(entityClass,
                        new FilterGroup().addFilter(idField, pkValue));
                if (list != null && !list.isEmpty()) {
                    before = list.get(0);
                }
            } catch (Exception e) {
                log.debug("审计回查旧值失败 entity={} pk={}", entityName, pkValue, e);
            }
        }

        // 两层去重：若已被注解层声明同 targetId，则只挂明细不重复生成兜底记录
        if (AuditContext.current().isDeclared(targetId)) {
            return;
        }

        // 构建明细
        List<AuditFieldChange> changes;
        AuditOperType operType;
        if (ct == CommandType.Insert) {
            changes = svc.diffService.insertDetail(entityName, newValue);
            operType = AuditOperType.CREATE;
        } else {
            changes = svc.diffService.diff(entityName, before, newValue);
            operType = AuditOperType.UPDATE;
        }

        AuditLogRecord auditLog = buildAuditLog(dao, entityName, em, targetId, newValue, operType, before, changes, svc);

        scheduleStore(auditLog, svc);
    }

    /** 在 beforeDelete 阶段处理：回查被删记录、构建明细、登记提交后落库。 */
    public static void handleDelete(Dao dao, String entityName, FilterGroup where,
                                    AuditServices svc) {
        EntityMeta em = MetaManager.singleInstance().getByEntityName(entityName);
        if (!shouldAudit(em, entityName, svc.properties)) {
            return;
        }
        Class<?> entityClass = entityClass(em);

        Map<String, Object> before = null;
        if (entityClass != null && where != null) {
            try {
                List<Map<String, Object>> list = dao.queryForMapList(entityClass, where);
                if (list != null && !list.isEmpty()) {
                    before = list.get(0);
                }
            } catch (Exception e) {
                log.debug("审计回查被删记录失败 entity={}", entityName, e);
            }
        }

        String targetId = before != null ? String.valueOf(before.get(idFieldName(em))) : null;
        if (AuditContext.current().isDeclared(targetId)) {
            return;
        }

        List<AuditFieldChange> changes = svc.diffService.deleteDetail(entityName, before);
        AuditOperType operType = AuditOperType.DELETE;
        AuditLogRecord auditLog = buildAuditLog(dao, entityName, em, targetId, before, operType, before, changes, svc);

        scheduleStore(auditLog, svc);
    }

    private static AuditLogRecord buildAuditLog(Dao dao, String entityName, EntityMeta em, String targetId,
                                          Map<String, Object> record, AuditOperType operType,
                                          Map<String, Object> before, List<AuditFieldChange> changes,
                                          AuditServices svc) {
        AuditLogRecord a = svc.auditLogService.create();
        a.setCaptureLayer(AuditCaptureLayer.ORM_FALLBACK.name());
        a.setOperType(operType.name());
        a.setOperName(operType.defaultName());
        a.setEntityName(entityName);
        a.setEntityTitle(svc.namer.entityTitle(entityName));
        if (em != null) {
            a.setTableName(em.getTableName());
        }
        a.setBizType(entityName);
        a.setTargetId(targetId);
        a.setTargetName(svc.namer.bizNameValue(record, null));

        // 摘要 + 明细
        String summary = svc.auditLogService.buildSummary(a, changes);
        a.setSummary(summary);
        a.setDetailJson(svc.auditLogService.toJson(changes));

        // 身份快照
        AuditActorSnapshot actor = svc.contextProvider.snapshot();
        svc.auditLogService.applyActor(a, actor);

        return a;
    }

    /**
     * 登记落库：在事务提交后才落库，避免记录被回滚的变更。
     *
     * <p>审计日志只记录已成功发生的数据变更。before 阶段无法预知 save 是否失败，
     * 若 save 抛异常，外层事务回滚，{@code afterCommit} 不会触发，
     * {@code afterCompletion} 收到 STATUS_ROLLED_BACK 时显式丢弃——即<b>失败的写操作不会被记录</b>。
     * 这符合审计语义：没改数据就不记。
     *
     * <p><b>无事务场景</b>：裸调用 {@code dao.save}（无 @Transactional 也无编程式事务）时，
     * SQL 执行成功即已"提交"，此时直接异步落库（此时已无回滚可能）。
     */
    private static void scheduleStore(AuditLogRecord auditLog, AuditServices svc) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // 注册事务同步：仅在提交后落库，回滚则丢弃
            final AuditLogRecord toStore = auditLog;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                private boolean committed = false;

                @Override
                public void afterCommit() {
                    committed = true;
                    svc.auditLogService.asyncLog(toStore);
                }

                @Override
                public void afterCompletion(int status) {
                    // 防御性：若未走 afterCommit（即回滚），确保不落库
                    if (!committed) {
                        log.debug("审计记录因事务回滚(status={})被丢弃: {} {}",
                                status, toStore.getOperType(), toStore.getTargetName());
                    }
                }
            });
        } else {
            // 无事务同步：SQL 已成功执行即视为提交，直接异步落库
            svc.auditLogService.asyncLog(auditLog);
        }
    }

    /** 判断是否审计此实体：总开关 + 表名不在黑名单 + 元数据可解析。 */
    private static boolean shouldAudit(EntityMeta em, String entityName, AuditLogProperties properties) {
        if (!properties.isEnabled()) {
            return false;
        }
        if (entityName == null || entityName.isEmpty()) {
            return false;
        }
        if (em == null) {
            return false;
        }
        String tableName = em.getTableName();
        if (tableName == null || properties.isExcludedTable(tableName)) {
            return false;
        }
        // 跳过影子表（*_readonly）
        if (tableName.endsWith("_readonly")) {
            return false;
        }
        return true;
    }

    private static Class<?> entityClass(EntityMeta em) {
        if (em == null) {
            return null;
        }
        try {
            return em.getClassType();
        } catch (Exception e) {
            return null;
        }
    }

    private static String idFieldName(EntityMeta em) {
        try {
            if (em != null && em.getId() != null && em.getId().getFieldName() != null) {
                return em.getId().getFieldName();
            }
        } catch (Exception ignore) {
        }
        return "id";
    }

    /** 监听器依赖的服务集合（由 Spring 注入后传入）。 */
    public static class AuditServices {
        public final AuditLogService auditLogService;
        public final AuditDiffService diffService;
        public final AuditBusinessNamer namer;
        public final AuditContextProvider contextProvider;
        public final AuditLogProperties properties;

        public AuditServices(AuditLogService auditLogService, AuditDiffService diffService,
                             AuditBusinessNamer namer, AuditContextProvider contextProvider,
                             AuditLogProperties properties) {
            this.auditLogService = auditLogService;
            this.diffService = diffService;
            this.namer = namer;
            this.contextProvider = contextProvider;
            this.properties = properties;
        }
    }
}
