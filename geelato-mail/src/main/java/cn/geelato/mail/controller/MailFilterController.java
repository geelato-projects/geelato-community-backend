package cn.geelato.mail.controller;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import cn.geelato.mail.entity.MailFilter;
import cn.geelato.mail.service.MailFilterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 邮件过滤器 Controller（P3-V79，7 个端点）。
 *
 * 由 ApiPrefixAutoConfiguration 自动加 /api 前缀。实际路径 = /api/mail/filters。
 *
 * 端点列表：
 * - GET    /filters                     当前用户过滤器列表（sortOrder 升序）
 * - POST   /filters                     创建（条件/动作结构校验，非法 40000）
 * - PATCH  /filters/{id}                局部更新（归属校验，越权/不存在 40400）
 * - DELETE /filters/{id}                逻辑删除（应用历史保留）
 * - POST   /filters/reorder             批量排序（{ids:[...]} 按顺序重排 1..n）
 * - POST   /filters/{id}/apply-existing 手动应用到既有收件箱邮件，返回 {applied}
 * - GET    /filters/{id}/apply-history  手动应用历史（按时间倒序）
 *
 * 匹配/动作执行语义见 MailFilterService 类级文档。autoReply 动作仅持久化不执行
 * （真实发送属引擎范畴）。
 *
 * 数据隔离：全部按当前登录用户 userId 过滤；修改/删除/排序/应用做归属校验。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@ApiRuntimeRestController("/mail/filters")
public class MailFilterController {

    @Autowired
    private MailFilterService filterService;

    /** 当前用户过滤器列表 */
    @GetMapping
    public ApiResult<List<Map<String, Object>>> list() {
        return ApiResult.success(filterService.list());
    }

    /** 创建过滤器（返回完整过滤器对象，含雪花 id 与 createdAt） */
    @PostMapping
    public ApiResult<Map<String, Object>> create(@RequestBody FilterRequest req) {
        MailFilter filter;
        try {
            filter = filterService.create(req.getName(), req.getEnabled(), req.getConditions(),
                    req.getAction(), req.getSortOrder(), req.getApplyToExisting());
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(40000, e.getMessage());
        }
        return ApiResult.success(filterService.toResponse(filter));
    }

    /** 局部更新过滤器（仅更新出现字段；conditions/action 出现即整体验证后替换） */
    @PatchMapping("/{id}")
    public ApiResult<Map<String, Object>> update(@PathVariable String id, @RequestBody FilterRequest req) {
        MailFilter filter = filterService.getOwned(id);
        if (filter == null) {
            return ApiResult.fail(40400, "过滤器不存在: " + id);
        }
        try {
            filterService.update(filter, req.getName(), req.getEnabled(), req.getConditions(),
                    req.getAction(), req.getSortOrder(), req.getApplyToExisting());
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(40000, e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        return ApiResult.success(result);
    }

    /** 逻辑删除过滤器（应用历史保留，供审计回溯） */
    @DeleteMapping("/{id}")
    public ApiResult<Map<String, Object>> delete(@PathVariable String id) {
        MailFilter filter = filterService.getOwned(id);
        if (filter == null) {
            return ApiResult.fail(40400, "过滤器不存在: " + id);
        }
        filterService.delete(filter);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        return ApiResult.success(result);
    }

    /** 批量排序：按 ids 顺序将 sortOrder 重排为 1..n（含越权/不存在 id 整体 40000） */
    @PostMapping("/reorder")
    public ApiResult<Map<String, Object>> reorder(@RequestBody ReorderRequest req) {
        try {
            filterService.reorder(req.getIds());
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(40000, e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        return ApiResult.success(result);
    }

    /**
     * 手动应用过滤器到既有收件箱邮件（folder='inbox' 非草稿）。
     * 动作预校验失败（如引用标签悬空）fail-fast 40000 且不写任何邮件。
     *
     * @return {applied: 匹配并应用动作的邮件数}
     */
    @PostMapping("/{id}/apply-existing")
    public ApiResult<Map<String, Object>> applyExisting(@PathVariable String id) {
        MailFilter filter = filterService.getOwned(id);
        if (filter == null) {
            return ApiResult.fail(40400, "过滤器不存在: " + id);
        }
        int applied;
        try {
            applied = filterService.applyToExisting(filter);
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(40000, e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("applied", applied);
        return ApiResult.success(result);
    }

    /** 过滤器手动应用历史（按应用时间倒序） */
    @GetMapping("/{id}/apply-history")
    public ApiResult<List<Map<String, Object>>> applyHistory(@PathVariable String id) {
        MailFilter filter = filterService.getOwned(id);
        if (filter == null) {
            return ApiResult.fail(40400, "过滤器不存在: " + id);
        }
        return ApiResult.success(filterService.applyHistory(id));
    }

    /** 过滤器请求（与前端 createMailFilter/updateMailFilter 对齐） */
    @lombok.Data
    public static class FilterRequest {
        private String name;
        private Boolean enabled;
        private List<Map<String, Object>> conditions;
        private Map<String, Object> action;
        private Integer sortOrder;
        private Boolean applyToExisting;
    }

    /** 批量排序请求（与前端 reorderMailFilters({ids}) 对齐） */
    @lombok.Data
    public static class ReorderRequest {
        private List<String> ids;
    }
}
