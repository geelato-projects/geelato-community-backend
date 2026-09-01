package cn.geelato.mail.controller;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import cn.geelato.mail.entity.MailAccount;
import cn.geelato.mail.entity.MailMessage;
import cn.geelato.mail.service.MailAccountService;
import cn.geelato.mail.service.MailMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 邮件草稿 Controller（P1-V75，3 个端点）。
 *
 * 由 ApiPrefixAutoConfiguration 自动加 /api 前缀。实际路径 = /api/mail/draft。
 *
 * 端点列表：
 * - POST   /draft        新建草稿（落 mail_message：folder='draft' + is_draft=1）
 * - PUT    /draft/{id}   更新草稿（全量覆盖写字段；前端始终提交完整写信表单状态）
 * - DELETE /draft/{id}   逻辑删除草稿（发送成功后清理/用户主动删除）
 *
 * 设计说明：草稿不建独立表，草稿列表/详情复用 GET /list?folder=draft 与 GET /{id}，
 * 与主流 Webmail（Drafts 文件夹存消息）一致；扩展字段落 draft_ext_json 并在详情响应回显。
 *
 * 数据隔离：全部按当前登录用户 userId 过滤；更新/删除做归属与草稿身份双重校验。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@ApiRuntimeRestController("/mail/draft")
public class MailDraftController {

    @Autowired
    private MailAccountService accountService;

    @Autowired
    private MailMessageService messageService;

    /** 新建草稿（允许无收件人，用户可随时暂存）；返回 {id} */
    @PostMapping
    public ApiResult<Map<String, Object>> save(@RequestBody MailMessageService.ComposeRequest compose) {
        MailAccount account = resolveAccount(compose.getFromAccountId());
        if (account == null) {
            return ApiResult.fail(40400, "邮箱账户不存在或未配置，请先在设置中添加邮箱账户后再保存草稿");
        }
        String id = messageService.saveDraft(account, compose);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        return ApiResult.success(result);
    }

    /** 更新草稿（归属 + 草稿身份校验；可随表单切换发件账户）；返回 {id} */
    @PutMapping("/{id}")
    public ApiResult<Map<String, Object>> update(@PathVariable String id,
                                                 @RequestBody MailMessageService.ComposeRequest compose) {
        MailMessage draft = messageService.getOwnedDraft(id);
        if (draft == null) {
            return ApiResult.fail(40400, "草稿不存在: " + id);
        }
        MailAccount account = resolveAccount(compose.getFromAccountId());
        if (account == null) {
            return ApiResult.fail(40400, "邮箱账户不存在或未配置，无法更新草稿");
        }
        messageService.updateDraft(draft, account, compose);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        return ApiResult.success(result);
    }

    /** 删除草稿（逻辑删除；归属 + 草稿身份校验） */
    @DeleteMapping("/{id}")
    public ApiResult<Map<String, Object>> delete(@PathVariable String id) {
        MailMessage draft = messageService.getOwnedDraft(id);
        if (draft == null) {
            return ApiResult.fail(40400, "草稿不存在: " + id);
        }
        messageService.deleteDraft(draft);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        return ApiResult.success(result);
    }

    /** 解析目标账户：显式 id → 默认账户 → 唯一账户；均无返回 null（与 MailController 同策略） */
    private MailAccount resolveAccount(String accountId) {
        if (accountId != null && !accountId.isBlank()) {
            return accountService.getOwned(accountId);
        }
        List<MailAccount> accounts = accountService.listByCurrentUser();
        for (MailAccount a : accounts) {
            if (a.getIsDefault() == 1) {
                return a;
            }
        }
        return accounts.size() == 1 ? accounts.get(0) : null;
    }
}
