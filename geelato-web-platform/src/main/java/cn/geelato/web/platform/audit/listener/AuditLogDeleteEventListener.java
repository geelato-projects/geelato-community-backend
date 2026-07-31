package cn.geelato.web.platform.audit.listener;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.orm.event.BeforeDeleteEventListener;
import cn.geelato.core.orm.event.DeleteEventContext;
import cn.geelato.web.platform.audit.listener.OrmAuditCollector.AuditServices;
import lombok.extern.slf4j.Slf4j;

/**
 * 第2层 ORM 兜底——删除事件监听器。
 *
 * <p>注册到 {@code DeleteEventManager} 的 before 阶段（同步），在删除前回查被删记录，
 * 构建审计记录并登记在事务提交后落库。仅处理未被第1层注解声明的删除（去重）。
 */
@Slf4j
public class AuditLogDeleteEventListener implements BeforeDeleteEventListener {

    private final AuditServices services;

    public AuditLogDeleteEventListener(AuditServices services) {
        this.services = services;
    }

    @Override
    public boolean enabled(DeleteEventContext context) {
        return services.properties.isEnabled();
    }

    @Override
    public boolean supports(DeleteEventContext context) {
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
    public void beforeDelete(DeleteEventContext context) {
        try {
            if (context.getCommand() == null) {
                return;
            }
            String entityName = context.getCommand().getEntityName();
            OrmAuditCollector.handleDelete(
                    context.getDao(),
                    entityName,
                    context.getCommand().getWhere(),
                    services);
        } catch (Exception e) {
            log.warn("审计(删除)兜底处理失败，已忽略", e);
        }
    }

    @Override
    public void afterDelete(DeleteEventContext context) {
        // 不在 after 处理
    }
}
