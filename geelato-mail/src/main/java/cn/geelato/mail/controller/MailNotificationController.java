package cn.geelato.mail.controller;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import cn.geelato.mail.entity.MailMessage;
import cn.geelato.mail.service.MailMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 邮件新邮件通知 Controller（P3-V79，1 个端点）。
 *
 * 由 ApiPrefixAutoConfiguration 自动加 /api 前缀。实际路径 = /api/mail/notifications。
 *
 * 端点列表：
 * - GET /notifications   收件箱未读数 + 最新一封收件箱邮件（前端轮询契约
 *   {unreadCount, latestMail?}；通知开关设置并入 settings/general.enableNotifications）
 *
 * 数据隔离：全部按当前登录用户 userId 过滤。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@ApiRuntimeRestController("/mail/notifications")
public class MailNotificationController {

    @Autowired
    private MailMessageService messageService;

    /** 收件箱未读数 + 最新一封收件箱邮件（无邮件时仅返回 unreadCount=0） */
    @GetMapping
    public ApiResult<Map<String, Object>> get() {
        Map<String, long[]> counts = messageService.folderCounts(null);
        long[] inbox = counts.get("inbox");
        Map<String, Object> result = new LinkedHashMap<>();
        // int 输出：JacksonConfig 全局 Long→String 序列化会输出 "0" 字符串，
        // 前端 MailNotificationSchema.unreadCount 为 z.number()，计数用 int 精度足够且契约对齐
        result.put("unreadCount", inbox == null ? 0 : (int) inbox[0]);
        MailMessage latest = messageService.findLatestInbox();
        if (latest != null) {
            result.put("latestMail", messageService.toResponse(latest));
        }
        return ApiResult.success(result);
    }
}
