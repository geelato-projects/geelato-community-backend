package cn.geelato.web.platform.audit.web;

import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.common.annotation.IgnoreSrvLog;
import cn.geelato.web.platform.audit.model.AuditLogRecord;
import cn.geelato.web.platform.audit.model.AuditLogQuery;
import cn.geelato.web.platform.audit.store.AuditLogStore;
import cn.geelato.web.platform.audit.store.DbAuditLogStore;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 业务审计日志查询接口。
 *
 * <p>面向运维/管理员：
 * <ul>
 *   <li>{@code GET /audit/page}：多条件分页查询（列表默认返回 summary，不返回 detail_json）；</li>
 *   <li>{@code GET /audit/detail/{id}}：单条详情（含完整 detail_json）；</li>
 *   <li>{@code GET /audit/timeline}：按业务对象查操作时间线（审计回溯核心场景）；</li>
 *   <li>{@code GET /audit/by-delegator}：按委托人查代客操作记录。</li>
 * </ul>
 *
 * <p>数据权限复用平台查询过滤（{@code PlatformQueryFilterSupport}）。
 * 控制器内直接用 {@link AuditLogStore}，避免绕过 ORM 查询。
 */
@IgnoreSrvLog
@ApiRestController("/audit")
public class AuditLogController {

    private final AuditLogStore store;

    public AuditLogController(AuditLogStore store) {
        this.store = store;
    }

    /**
     * 分页查询审计日志。
     * 列表默认返回摘要，不返回 detailJson（大字段）。
     */
    @GetMapping("/page")
    public ApiPagedResult<AuditLogRecord> page(
            @RequestParam(value = "actorId", required = false) String actorId,
            @RequestParam(value = "delegatorId", required = false) String delegatorId,
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestParam(value = "operType", required = false) String operType,
            @RequestParam(value = "operName", required = false) String operName,
            @RequestParam(value = "targetId", required = false) String targetId,
            @RequestParam(value = "tenantCode", required = false) String tenantCode,
            @RequestParam(value = "entityName", required = false) String entityName,
            @RequestParam(value = "fromTime", required = false) Long fromTime,
            @RequestParam(value = "toTime", required = false) Long toTime,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNum", required = false, defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") int pageSize) {

        AuditLogQuery q = new AuditLogQuery();
        q.setActorId(actorId);
        q.setDelegatorId(delegatorId);
        q.setBizType(bizType);
        q.setOperType(operType);
        q.setOperName(operName);
        q.setTargetId(targetId);
        q.setTenantCode(tenantCode);
        q.setEntityName(entityName);
        q.setFromTime(fromTime);
        q.setToTime(toTime);
        q.setKeyword(keyword);
        q.setPageNum(pageNum);
        q.setPageSize(pageSize);

        ApiPagedResult<AuditLogRecord> result1 = store.page(q);
        // 列表页剥除大字段，降低传输
        if (result1 != null && result1.getData() != null) {
            stripDetail(result1);
        }
        return result1;
    }

    /**
     * 单条详情（含完整 detailJson）。
     */
    @GetMapping("/detail/{id}")
    public ApiResult<AuditLogRecord> detail(@PathVariable("id") String id) {
        if (!StringUtils.hasText(id)) {
            return ApiResult.fail("id 不能为空");
        }
        if (!(store instanceof DbAuditLogStore dbStore)) {
            return ApiResult.fail("当前存储实现不支持详情查询");
        }
        AuditLogRecord log = dbStore.getById(id);
        if (log == null) {
            return ApiResult.fail("审计记录不存在");
        }
        return ApiResult.success(log);
    }

    /**
     * 按业务对象查操作时间线（审计回溯核心场景）。
     * 例：查询运单 WBL-2024-001 的全部操作历史。
     */
    @GetMapping("/timeline")
    public ApiResult<List<AuditLogRecord>> timeline(
            @RequestParam("bizType") String bizType,
            @RequestParam("targetId") String targetId) {
        if (!StringUtils.hasText(bizType) || !StringUtils.hasText(targetId)) {
            return ApiResult.fail("bizType 和 targetId 必填");
        }
        if (!(store instanceof DbAuditLogStore dbStore)) {
            return ApiResult.fail("当前存储实现不支持 timeline 查询");
        }
        List<AuditLogRecord> list = dbStore.timeline(bizType, targetId);
        stripDetail(list);
        return ApiResult.success(list);
    }

    /**
     * 按委托人查代客操作记录（双向追溯）。
     */
    @GetMapping("/by-delegator")
    public ApiPagedResult<AuditLogRecord> byDelegator(
            @RequestParam("delegatorId") String delegatorId,
            @RequestParam(value = "pageNum", required = false, defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") int pageSize) {
        AuditLogQuery q = new AuditLogQuery();
        q.setDelegatorId(delegatorId);
        q.setPageNum(pageNum);
        q.setPageSize(pageSize);
        ApiPagedResult<AuditLogRecord> result = store.page(q);
        if (result != null) {
            stripDetail(result);
        }
        return result;
    }

    /** 剥除列表中的 detailJson（大字段），保留 summary。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stripDetail(ApiPagedResult result) {
        Object data = result.getData();
        if (data instanceof List list) {
            stripDetail(list);
        }
    }

    private void stripDetail(List<AuditLogRecord> list) {
        if (list == null) {
            return;
        }
        for (AuditLogRecord a : list) {
            a.setDetailJson(null);
            a.setMetadata(null);
        }
    }
}
