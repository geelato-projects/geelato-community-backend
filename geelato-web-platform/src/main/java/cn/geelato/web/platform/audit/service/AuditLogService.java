package cn.geelato.web.platform.audit.service;

import cn.geelato.web.platform.audit.boot.AuditLogProperties;
import cn.geelato.web.platform.audit.context.AuditActorSnapshot;
import cn.geelato.web.platform.audit.enums.AuditCaptureLayer;
import cn.geelato.web.platform.audit.model.AuditFieldChange;
import cn.geelato.web.platform.audit.model.AuditLogRecord;
import cn.geelato.web.platform.audit.store.AuditLogStore;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 审计日志门面服务：统一构建 + 异步落库 + 失败降级。
 *
 * <p>两层捕获（注解切面 / ORM 监听器）最终都通过本服务的 {@link #asyncLog} / {@link #syncLog} 落库。
 * 落库走独立线程池，失败降级写文件日志，保证审计异常绝不影响业务。
 */
@Slf4j
@Service
public class AuditLogService {

    private final AuditLogStore store;
    private final AuditLogProperties properties;
    private final ExecutorService executor;

    /** 文件降级日志的 logger 名（按需在 logback 配置输出到 audit.log）。 */
    private static final org.slf4j.Logger FALLBACK_LOG = org.slf4j.LoggerFactory.getLogger("geelato.platform.audit.fallback");

    @Autowired
    public AuditLogService(AuditLogStore store, AuditLogProperties properties) {
        this.store = store;
        this.properties = properties;
        this.executor = Executors.newFixedThreadPool(
                Math.max(properties.getStoreThreadPoolSize(), 1),
                new AuditThreadFactory());
    }

    /** 构建一条审计记录（不落库），由切面/监听器填充业务字段后调用。 */
    public AuditLogRecord create() {
        return new AuditLogRecord();
    }

    /**
     * 异步落库。对关键审计（删除/审批等）按配置可选同步落库。
     */
    public void asyncLog(AuditLogRecord auditLog) {
        if (!properties.isEnabled() || auditLog == null) {
            return;
        }
        if (auditLog.getOperateAt() == null) {
            auditLog.setOperateAt(new Date());
        }
        if (properties.isStoreSyncForCritical() && isCritical(auditLog)) {
            syncLog(auditLog);
            return;
        }
        executor.submit(() -> doStore(auditLog));
    }

    /** 同步落库（极少用，仅关键审计需要强一致时）。 */
    public void syncLog(AuditLogRecord auditLog) {
        if (!properties.isEnabled() || auditLog == null) {
            return;
        }
        if (auditLog.getOperateAt() == null) {
            auditLog.setOperateAt(new Date());
        }
        doStore(auditLog);
    }

    private void doStore(AuditLogRecord auditLog) {
        try {
            store.store(auditLog);
        } catch (Exception e) {
            log.warn("审计日志落库失败，降级写文件: {}", auditLog.getSummary(), e);
            if (properties.isStoreFailToFile()) {
                try {
                    FALLBACK_LOG.warn("[AUDIT-FALLBACK] {}", safeToJson(auditLog));
                } catch (Exception ignore) {
                    // 降级也失败则彻底放弃，绝不让审计影响业务
                }
            }
        }
    }

    /** 判定是否为"关键审计"（影响同步落库决策）。 */
    private boolean isCritical(AuditLogRecord auditLog) {
        String t = auditLog.getOperType();
        return "DELETE".equals(t) || "APPROVE".equals(t) || "TERMINATE".equals(t);
    }

    /**
     * 构建中文业务摘要。
     * <p>注解层："{actor} {operName}了 {entityTitle} {targetName}，{变化摘要}"
     * <p>兜底层："{actor} {operName} {entityTitle} {targetName}（N个字段）"
     */
    public String buildSummary(AuditLogRecord auditLog, List<AuditFieldChange> changes) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(auditLog.getActorName())) {
            sb.append(auditLog.getActorName());
        }
        if (StringUtils.hasText(auditLog.getDelegatorId()) && StringUtils.hasText(auditLog.getDelegatorName())) {
            sb.append("(代").append(auditLog.getDelegatorName()).append(")");
        }
        sb.append(" ");

        String operName = StringUtils.hasText(auditLog.getOperName()) ? auditLog.getOperName() : "操作";
        // 兜底层（UPDATE/DELETE/CREATE）的动作名已含"了"，注解层的可能不含，这里统一处理
        if (!operName.endsWith("了") && AuditCaptureLayer.ORM_FALLBACK.name().equals(auditLog.getCaptureLayer())) {
            operName = operName + "了";
        }
        sb.append(operName);

        if (StringUtils.hasText(auditLog.getEntityTitle())) {
            sb.append(" ").append(auditLog.getEntityTitle());
        }
        if (StringUtils.hasText(auditLog.getTargetName())) {
            sb.append(" ").append(auditLog.getTargetName());
        }

        // 变化摘要
        if (changes != null && !changes.isEmpty()) {
            int max = properties.getSummaryMaxFields();
            int shown = Math.min(changes.size(), max);
            sb.append("，");
            for (int i = 0; i < shown; i++) {
                AuditFieldChange c = changes.get(i);
                if (i > 0) {
                    sb.append("，");
                }
                sb.append(StringUtils.hasText(c.getTitle()) ? c.getTitle() : c.getField());
                Object od = c.getOldDisplay() != null ? c.getOldDisplay() : c.getOldValue();
                Object nd = c.getNewDisplay() != null ? c.getNewDisplay() : c.getNewValue();
                if (od != null && nd != null) {
                    sb.append(":").append(od).append("→").append(nd);
                } else if (nd != null) {
                    sb.append("→").append(nd);
                } else if (od != null) {
                    sb.append(":").append(od);
                }
            }
            if (changes.size() > max) {
                sb.append("等共").append(changes.size()).append("项");
            }
        }
        return sb.toString();
    }

    /** 应用身份快照到审计记录。 */
    public void applyActor(AuditLogRecord auditLog, AuditActorSnapshot actor) {
        if (actor == null) {
            return;
        }
        auditLog.setActorId(actor.getActorId());
        auditLog.setActorName(actor.getActorName());
        auditLog.setActorType(actor.getActorType());
        auditLog.setDelegatorId(actor.getDelegatorId());
        auditLog.setDelegatorName(actor.getDelegatorName());
        auditLog.setTenantCode(actor.getTenantCode());
        auditLog.setOrgId(actor.getOrgId());
        auditLog.setDeptId(actor.getDeptId());
        auditLog.setBuId(actor.getBuId());
        auditLog.setClientId(actor.getClientId());
        auditLog.setSessionId(actor.getSessionId());
        auditLog.setIp(actor.getIp());
        auditLog.setUserAgent(actor.getUserAgent());
        auditLog.setRequestId(actor.getRequestId());
        auditLog.setTraceId(actor.getTraceId());
    }

    /** 序列化 detail changes 为 JSON。 */
    public String toJson(List<AuditFieldChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return null;
        }
        return JSON.toJSONString(changes);
    }

    private String safeToJson(AuditLogRecord auditLog) {
        try {
            return JSON.toJSONString(auditLog);
        } catch (Exception e) {
            return "operName=" + auditLog.getOperName() + ",targetId=" + auditLog.getTargetId();
        }
    }

    /** 优雅关闭线程池（容器销毁时）。 */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static class AuditThreadFactory implements ThreadFactory {
        private final AtomicInteger idx = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "audit-log-" + idx.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
