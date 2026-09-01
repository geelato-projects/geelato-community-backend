package cn.geelato.mail.controller;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import cn.geelato.mail.entity.MailLabel;
import cn.geelato.mail.service.MailLabelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 邮件标签 Controller（P1-V75，4 个端点）。
 *
 * 由 ApiPrefixAutoConfiguration 自动加 /api 前缀。实际路径 = /api/mail/labels。
 *
 * 端点列表：
 * - GET    /labels?accountId   当前用户标签列表（含未读数聚合；accountId 可空=全部）
 * - POST   /labels             创建（{name,color}，accountId 可空=用户级共享标签）
 * - PATCH  /labels/{id}        重命名/改色/调整排序
 * - DELETE /labels/{id}        逻辑删除（同步从当前用户邮件 label_ids 中摘除）
 *
 * 数据隔离：全部按当前登录用户 userId 过滤；修改/删除做归属校验。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@ApiRuntimeRestController("/mail/labels")
public class MailLabelController {

    @Autowired
    private MailLabelService labelService;

    /** 当前用户标签列表（含各标签未读邮件数） */
    @GetMapping
    public ApiResult<List<Map<String, Object>>> list(@RequestParam(required = false) String accountId) {
        return ApiResult.success(labelService.list(accountId));
    }

    /** 创建标签（name 必填；color 缺省 #165dff；sortOrder 取当前用户最大值 + 1） */
    @PostMapping
    public ApiResult<Map<String, Object>> create(@RequestBody LabelRequest req) {
        if (req.getName() == null || req.getName().isBlank()) {
            return ApiResult.fail(40000, "标签名称不能为空");
        }
        MailLabel label = labelService.create(req.getName(), req.getColor(), req.getAccountId());
        return ApiResult.success(labelService.toResponse(label, 0));
    }

    /** 更新标签（重命名/改色/排序；仅允许操作本人标签） */
    @PatchMapping("/{id}")
    public ApiResult<Map<String, Object>> update(@PathVariable String id, @RequestBody LabelRequest req) {
        MailLabel label = labelService.getOwned(id);
        if (label == null) {
            return ApiResult.fail(40400, "标签不存在: " + id);
        }
        labelService.update(label, req.getName(), req.getColor(), req.getSortOrder());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        return ApiResult.success(result);
    }

    /** 删除标签（逻辑删除），并从当前用户全部邮件的 label_ids 中摘除该标签 */
    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<Map<String, Object>> delete(@PathVariable String id) {
        MailLabel label = labelService.getOwned(id);
        if (label == null) {
            return ApiResult.fail(40400, "标签不存在: " + id);
        }
        labelService.delete(label);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        return ApiResult.success(result);
    }

    /** 标签请求（与前端 createLabel/updateLabel 对齐） */
    @lombok.Data
    public static class LabelRequest {
        private String name;
        private String color;
        private Integer sortOrder;
        private String accountId;
    }
}
