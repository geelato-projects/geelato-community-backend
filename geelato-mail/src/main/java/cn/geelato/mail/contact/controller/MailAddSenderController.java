package cn.geelato.mail.contact.controller;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import cn.geelato.mail.contact.entity.MailContact;
import cn.geelato.mail.contact.service.MailContactService;
import cn.geelato.mail.entity.MailMessage;
import cn.geelato.mail.service.MailMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;

/**
 * 添加发件人到联系人（P2-V78；前端契约 addSenderToContact，TODO-PRODUCT P3-DL5 暂无 UI 调用方）。
 *
 * 由 ApiPrefixAutoConfiguration 自动加 /api 前缀。实际路径 = /api/mail/{id}/add-sender。
 *
 * 幂等语义：发件人邮箱已在当前用户通讯录（忽略大小写）时直接返回既有联系人，
 * 不重复创建（与 (user_id + lower(email)) 去重口径一致）。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@ApiRuntimeRestController("/mail")
public class MailAddSenderController {

    @Autowired
    private MailMessageService messageService;

    @Autowired
    private MailContactService contactService;

    /** 将邮件发件人加入联系人（归属校验；已存在返回既有联系人） */
    @PostMapping("/{id}/add-sender")
    public ApiResult<Map<String, Object>> addSender(@PathVariable String id) {
        MailMessage msg = messageService.getOwned(id);
        if (msg == null) {
            return ApiResult.fail(40400, "邮件不存在: " + id);
        }
        String email = MailContactService.normalizeEmail(msg.getFromEmail());
        if (email == null || email.isEmpty()) {
            return ApiResult.fail(40000, "邮件缺少发件人邮箱: " + id);
        }
        MailContact existing = contactService.findByEmail(email);
        if (existing != null) {
            return ApiResult.success(contactService.toResponse(existing, null));
        }
        String name = MailContactService.blankToNull(msg.getFromName());
        MailContact contact;
        try {
            contact = contactService.create(name == null ? email.substring(0, email.indexOf('@')) : name,
                    email, null, null, null, null, null, msg.getAccountId());
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(40000, e.getMessage());
        }
        return ApiResult.success(contactService.toResponse(contact, null));
    }
}
