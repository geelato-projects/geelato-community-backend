package cn.geelato.web.platform.audit.store;

import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.core.mql.filter.FilterGroup.Operator;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.mql.parser.PageQueryRequest;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.web.platform.audit.model.AuditLogRecord;
import cn.geelato.web.platform.audit.model.AuditLogQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

/**
 * 默认审计日志存储实现：写入 MySQL {@code platform_audit_log} 表。
 *
 * <p>基于平台 {@link Dao}，落库走 {@code dao.save(AuditLog)}。审计表在 ORM 监听器黑名单内，
 * 故 save 自身不会触发审计递归。
 */
@Slf4j
@Component
public class DbAuditLogStore implements AuditLogStore {

    private final Dao dao;

    @Autowired
    public DbAuditLogStore(@Qualifier("primaryDao") Dao dao) {
        this.dao = dao;
    }

    @Override
    public void store(AuditLogRecord auditLog) {
        if (auditLog == null) {
            return;
        }
        if (auditLog.getOperateAt() == null) {
            auditLog.setOperateAt(new Date());
        }
        dao.save(auditLog);
    }

    @Override
    public ApiPagedResult<AuditLogRecord> page(AuditLogQuery query) {
        FilterGroup fg = buildFilter(query);
        PageQueryRequest pageReq = new PageQueryRequest();
        pageReq.setPageNum(Math.max(query.getPageNum(), 1));
        pageReq.setPageSize(Math.max(Math.min(query.getPageSize(), 200), 1));
        pageReq.setOrderBy("operate_at desc");
        return dao.pageQueryResult(AuditLogRecord.class, fg, pageReq);
    }

    /** 按主键查单条审计记录（详情页用）。 */
    public AuditLogRecord getById(String id) {
        if (!StringUtils.hasText(id)) {
            return null;
        }
        FilterGroup fg = new FilterGroup();
        fg.addFilter("id", Operator.eq, id);
        List<AuditLogRecord> list = dao.queryList(AuditLogRecord.class, fg, null);
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }

    /**
     * 按业务对象查时间线（审计回溯核心场景）。
     *
     * @param bizType  业务类型
     * @param targetId 业务对象主键
     * @return 该业务对象的完整操作历史（按时间升序）
     */
    public List<AuditLogRecord> timeline(String bizType, String targetId) {
        FilterGroup fg = new FilterGroup();
        fg.addFilter("bizType", Operator.eq, bizType);
        fg.addFilter("targetId", Operator.eq, targetId);
        return dao.queryList(AuditLogRecord.class, fg, "operate_at asc");
    }

    private FilterGroup buildFilter(AuditLogQuery q) {
        FilterGroup fg = new FilterGroup();
        if (StringUtils.hasText(q.getActorId())) {
            fg.addFilter("actorId", Operator.eq, q.getActorId());
        }
        if (StringUtils.hasText(q.getDelegatorId())) {
            fg.addFilter("delegatorId", Operator.eq, q.getDelegatorId());
        }
        if (StringUtils.hasText(q.getBizType())) {
            fg.addFilter("bizType", Operator.eq, q.getBizType());
        }
        if (StringUtils.hasText(q.getOperType())) {
            fg.addFilter("operType", Operator.eq, q.getOperType());
        }
        if (StringUtils.hasText(q.getOperName())) {
            fg.addFilter("operName", Operator.contains, q.getOperName());
        }
        if (StringUtils.hasText(q.getTargetId())) {
            fg.addFilter("targetId", Operator.eq, q.getTargetId());
        }
        if (StringUtils.hasText(q.getTenantCode())) {
            fg.addFilter("tenantCode", Operator.eq, q.getTenantCode());
        }
        if (StringUtils.hasText(q.getEntityName())) {
            fg.addFilter("entityName", Operator.eq, q.getEntityName());
        }
        if (q.getFromTime() != null) {
            fg.addFilter("operateAt", Operator.gte, String.valueOf(q.getFromTime()), new Date(q.getFromTime()));
        }
        if (q.getToTime() != null) {
            fg.addFilter("operateAt", Operator.lte, String.valueOf(q.getToTime()), new Date(q.getToTime()));
        }
        // 关键字：对 summary / operName / targetName 做或匹配（这里简化为对 summary contains，
        // 精确的多字段或匹配由查询层在需要时扩展）
        if (StringUtils.hasText(q.getKeyword())) {
            fg.addFilter("summary", Operator.contains, q.getKeyword());
        }
        return fg;
    }
}
