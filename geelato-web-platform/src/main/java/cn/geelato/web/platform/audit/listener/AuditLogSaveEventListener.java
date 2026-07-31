package cn.geelato.web.platform.audit.listener;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.orm.event.BeforeSaveEventListener;
import cn.geelato.core.orm.event.SaveEventContext;
import cn.geelato.web.platform.audit.boot.AuditLogProperties;
import cn.geelato.web.platform.audit.listener.OrmAuditCollector.AuditServices;
import lombok.extern.slf4j.Slf4j;

/**
 * 第2层 ORM 兜底——保存事件监听器。
 *
 * <p>注册到 {@code SaveEventManager} 的 before 阶段（同步），在写库前回查旧值、构建审计记录，
 * 登记在事务提交后落库。仅处理未被第1层注解声明的写操作（去重）。
 *
 * <p>所有业务逻辑在 {@link OrmAuditCollector#handleSave} 中，异常被捕获不影响业务。
 */
@Slf4j
public class AuditLogSaveEventListener implements BeforeSaveEventListener {

    private final AuditServices services;

    public AuditLogSaveEventListener(AuditServices services) {
        this.services = services;
    }

    @Override
    public boolean enabled(SaveEventContext context) {
        return services.properties.isEnabled();
    }

    @Override
    public boolean supports(SaveEventContext context) {
        if (!services.properties.isEnabled()) {
            return false;
        }
        String entityName = context.getCommand() != null ? context.getCommand().getEntityName() : null;
        if (entityName == null || entityName.isEmpty()) {
            return false;
        }
        try {
            EntityMeta em = MetaManager.singleInstance().getByEntityName(entityName);
            if (em == null || em.getTableName() == null) {
                return false;
            }
            if (services.properties.isExcludedTable(em.getTableName())) {
                return false;
            }
            if (em.getTableName().endsWith("_readonly")) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void beforeSave(SaveEventContext context) {
        try {
            if (context.getCommand() == null) {
                return;
            }
            EntityMeta em = MetaManager.singleInstance().getByEntityName(context.getCommand().getEntityName());
            OrmAuditCollector.handleSave(context.getDao(), context.getCommand(), em, services);
        } catch (Exception e) {
            // 审计异常绝不影响业务写操作
            log.warn("审计(保存)兜底处理失败，已忽略", e);
        }
    }

    @Override
    public void afterSave(SaveEventContext context) {
        // 不在 after 处理（after 为异步、事务外、可能已回滚），逻辑统一在 before + afterCommit
    }
}
