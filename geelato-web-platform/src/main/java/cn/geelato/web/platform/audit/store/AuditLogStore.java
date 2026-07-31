package cn.geelato.web.platform.audit.store;

import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.web.platform.audit.model.AuditLogRecord;
import cn.geelato.web.platform.audit.model.AuditLogQuery;

/**
 * 审计日志存储 SPI。
 *
 * <p>默认实现 {@code DbAuditLogStore}（写 MySQL {@code platform_audit_log} 表）。
 * 可替换为 ES 等实现。
 */
public interface AuditLogStore {

    /** 落库一条审计记录。 */
    void store(AuditLogRecord auditLog);

    /** 分页查询。 */
    ApiPagedResult<AuditLogRecord> page(AuditLogQuery query);
}
