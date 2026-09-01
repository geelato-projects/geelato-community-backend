package cn.geelato.mail.controller;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import cn.geelato.mail.service.MailSettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 邮件通用设置 Controller（P3-V79，2 个端点）。
 *
 * 由 ApiPrefixAutoConfiguration 自动加 /api 前缀。实际路径 = /api/mail/settings。
 *
 * 端点列表：
 * - GET   /settings/general   当前用户通用设置（未保存过返回默认值快照）
 * - PATCH /settings/general   Partial 合并更新（upsert 语义；未知键/非法值 40000）
 *
 * 通知开关并入 general.enableNotifications（前端契约字段），不独立建端点。
 *
 * 数据隔离：全部按当前登录用户 userId 过滤。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@ApiRuntimeRestController("/mail/settings")
public class MailSettingsController {

    @Autowired
    private MailSettingService settingService;

    /** 当前用户通用设置（10 字段完整快照，未保存过返回默认值） */
    @GetMapping("/general")
    public ApiResult<Map<String, Object>> getGeneral() {
        return ApiResult.success(settingService.getGeneral());
    }

    /** Partial 合并更新通用设置（upsert；键缺失=不动，出现即校验后合并） */
    @PatchMapping("/general")
    public ApiResult<Map<String, Object>> patchGeneral(@RequestBody Map<String, Object> patch) {
        try {
            settingService.patchGeneral(patch);
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(40000, e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        return ApiResult.success(result);
    }
}
