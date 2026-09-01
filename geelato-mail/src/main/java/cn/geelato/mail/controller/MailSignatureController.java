package cn.geelato.mail.controller;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import cn.geelato.mail.entity.MailSignature;
import cn.geelato.mail.service.MailSignatureService;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 邮件签名 Controller（P1-V75，4 个端点）。
 *
 * 由 ApiPrefixAutoConfiguration 自动加 /api 前缀。实际路径 = /api/mail/signatures。
 *
 * 端点列表：
 * - GET    /signatures?accountId   当前用户签名列表（默认在前；accountId 可空=全部）
 * - POST   /signatures             创建（{name,content}；首个签名自动设为默认）
 * - PATCH  /signatures/{id}        重命名/改内容/设默认（设默认时清除其他默认）
 * - DELETE /signatures/{id}        逻辑删除
 *
 * 数据隔离：全部按当前登录用户 userId 过滤；修改/删除做归属校验。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@ApiRuntimeRestController("/mail/signatures")
public class MailSignatureController {

    @Autowired
    private MailSignatureService signatureService;

    /** 当前用户签名列表（默认签名在前，其余按创建时间升序） */
    @GetMapping
    public ApiResult<List<Map<String, Object>>> list(@RequestParam(required = false) String accountId) {
        return ApiResult.success(signatureService.list(accountId));
    }

    /** 创建签名（name 必填；content 可空按空串存） */
    @PostMapping
    public ApiResult<Map<String, Object>> create(@RequestBody SignatureRequest req) {
        if (req.getName() == null || req.getName().isBlank()) {
            return ApiResult.fail(40000, "签名名称不能为空");
        }
        MailSignature signature = signatureService.create(
                req.getName(), req.getContent() == null ? "" : req.getContent(), req.getAccountId());
        return ApiResult.success(signatureService.toResponse(signature));
    }

    /** 更新签名（重命名/改内容/设默认；仅允许操作本人签名） */
    @PatchMapping("/{id}")
    public ApiResult<Map<String, Object>> update(@PathVariable String id, @RequestBody SignatureRequest req) {
        MailSignature signature = signatureService.getOwned(id);
        if (signature == null) {
            return ApiResult.fail(40400, "签名不存在: " + id);
        }
        signatureService.update(signature, req.getName(), req.getContent(), req.getIsDefault());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        return ApiResult.success(result);
    }

    /** 删除签名（逻辑删除；仅允许操作本人签名） */
    @DeleteMapping("/{id}")
    public ApiResult<Map<String, Object>> delete(@PathVariable String id) {
        MailSignature signature = signatureService.getOwned(id);
        if (signature == null) {
            return ApiResult.fail(40400, "签名不存在: " + id);
        }
        signatureService.delete(signature);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        return ApiResult.success(result);
    }

    /** 签名请求（与前端 createSignature/updateSignature 对齐） */
    @lombok.Data
    public static class SignatureRequest {
        private String name;
        private String content;
        private Boolean isDefault;
        private String accountId;
    }
}
