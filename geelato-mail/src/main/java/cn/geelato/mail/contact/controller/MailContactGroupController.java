package cn.geelato.mail.contact.controller;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import cn.geelato.mail.contact.entity.MailContactGroup;
import cn.geelato.mail.contact.service.MailContactGroupService;
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
 * 邮件联系人分组 Controller（P2-V78，矩阵附录 A P2 清单分组侧 4 个端点）。
 *
 * 由 ApiPrefixAutoConfiguration 自动加 /api 前缀。实际路径 = /api/mail/contact-groups。
 *
 * 端点列表：
 * - GET    /contact-groups        当前用户分组列表（含 contactCount 实时聚合）
 * - POST   /contact-groups        创建（{name}，用户级重名 fail-fast）
 * - PATCH  /contact-groups/{id}   重命名/调整排序（归属校验）
 * - DELETE /contact-groups/{id}   逻辑删除（分组下联系人置未分组）
 *
 * 数据隔离：全部按当前登录用户 userId 过滤；写操作做归属校验。
 * 分组为用户级（跨账户共享）；前端 queryContactGroups 的 accountId 参数不过滤分组。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@ApiRuntimeRestController("/mail/contact-groups")
public class MailContactGroupController {

    @Autowired
    private MailContactGroupService groupService;

    /** 分组列表（含各分组联系人计数） */
    @GetMapping
    public ApiResult<List<Map<String, Object>>> list() {
        return ApiResult.success(groupService.list());
    }

    /** 创建分组（name 必填；重名 fail-fast；sortOrder 取最大值 + 1） */
    @PostMapping
    public ApiResult<Map<String, Object>> create(@RequestBody GroupRequest req) {
        MailContactGroup group;
        try {
            group = groupService.create(req.getName());
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(40000, e.getMessage());
        }
        return ApiResult.success(groupService.toResponse(group, 0));
    }

    /** 更新分组（重命名/排序；仅允许操作本人分组） */
    @PatchMapping("/{id}")
    public ApiResult<Map<String, Object>> update(@PathVariable String id, @RequestBody GroupRequest req) {
        MailContactGroup group = groupService.getOwned(id);
        if (group == null) {
            return ApiResult.fail(40400, "分组不存在: " + id);
        }
        try {
            groupService.update(group, req.getName(), req.getSortOrder());
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(40000, e.getMessage());
        }
        return ApiResult.success(successResult());
    }

    /** 删除分组（逻辑删除；分组下联系人 group_id 置 NULL=未分组） */
    @DeleteMapping("/{id}")
    public ApiResult<Map<String, Object>> delete(@PathVariable String id) {
        MailContactGroup group = groupService.getOwned(id);
        if (group == null) {
            return ApiResult.fail(40400, "分组不存在: " + id);
        }
        groupService.delete(group);
        return ApiResult.success(successResult());
    }

    private static Map<String, Object> successResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        return result;
    }

    /** 分组请求（与前端 createContactGroup/updateContactGroup 对齐） */
    @lombok.Data
    public static class GroupRequest {
        private String name;
        private Integer sortOrder;
    }
}
