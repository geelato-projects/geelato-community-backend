package cn.geelato.mail.controller;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import cn.geelato.mail.contact.service.MailContactRecentService;
import cn.geelato.mail.entity.MailAccount;
import cn.geelato.mail.entity.MailMessage;
import cn.geelato.mail.service.MailAccountService;
import cn.geelato.mail.service.MailAttachmentStorageService;
import cn.geelato.mail.service.MailFilterService;
import cn.geelato.mail.service.MailMessageService;
import cn.geelato.mail.service.MailMimeSupport;
import cn.geelato.mail.service.MailProtocolService;
import cn.geelato.mail.service.MailSyncService;
import cn.geelato.mail.util.MailSessionCtx;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 邮件核心 Controller（P0 收发链路，15 个端点中的 11 个）。
 *
 * 由 ApiPrefixAutoConfiguration 自动加 /api 前缀（@ApiRuntimeRestController 注解）。
 * 实际路径 = /api/mail + 此处路径。
 *
 * 端点列表：
 * - GET  /accounts               当前用户邮箱账户列表（不含凭据密文）
 * - POST /accounts               创建账户（先连通性验证，失败拒建；凭据 AES-GCM 加密落库）
 * - POST /accounts/verify        连通性验证（IMAP 登录 + SMTP 连接）
 * - PUT/PATCH /accounts/{id}     局部更新账户（归属校验；凭据更新时重新 AES-GCM 加密）
 * - DELETE /accounts/{id}        逻辑删除账户（级联逻辑删除其邮件/自定义文件夹；默认账户删除后最早剩余账户接任）
 * - GET  /folders?accountId      系统文件夹 + 未读/总数聚合（starred/todo 为 flags 虚拟视图）
 * - POST /sync?accountId         手动触发 IMAP 收件箱同步（去重落库）
 * - GET  /list                   邮件分页列表（folder/keyword/operators/labelId/时间范围/排序）
 * - GET  /{id}                   邮件详情（归属校验）
 * - POST /send                   SMTP 发送（multipart/mixed 真实附件）+ 存发件箱副本（失败留痕 send_status='failed'）
 * - POST /batch                  批量操作（read/unread/star/delete/move/archive/spam/todo/setLabels 等）
 * - PATCH /{id}/note             更新用户备注（V75）
 * - GET  /{id}/source            邮件源码查看（IMAP 回源 RFC822 全文 + headers 解析；V76）
 * - POST /{id}/withdraw          撤回（SMTP 无真实撤回能力：诚实 success:false + withdraw_status='failed' 留痕；V76）
 * - GET  /bg-send/{taskId}       后台发送状态查询（taskId=发件箱副本 id，读 send_status 列；V76）
 * - POST /attachments/upload     附件真实上传（multipart，用户隔离落盘，返回 token；V76）
 * - GET  /{mailId}/attachments/{index} 附件内容下载（本地 token 优先，IMAP 回源兜底；V76）
 *
 * 自定义文件夹 CRUD 见 {@link MailFolderController}；草稿 CRUD 见 {@link MailDraftController}；
 * 标签/签名 CRUD 见 {@link MailLabelController}、{@link MailSignatureController}。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@Slf4j
@ApiRuntimeRestController("/mail")
public class MailController {

    @Autowired
    private MailAccountService accountService;

    @Autowired
    private MailMessageService messageService;

    @Autowired
    private MailProtocolService protocolService;

    @Autowired
    private MailAttachmentStorageService attachmentStorageService;

    @Autowired
    private MailContactRecentService contactRecentService;

    @Autowired
    private MailFilterService filterService;

    @Autowired
    private MailSyncService mailSyncService;

    // ==================== 账户 ====================

    /** 当前用户邮箱账户列表 */
    @GetMapping("/accounts")
    public ApiResult<List<Map<String, Object>>> accounts() {
        List<Map<String, Object>> list = accountService.listByCurrentUser().stream()
                .map(accountService::toResponse)
                .collect(Collectors.toList());
        return ApiResult.success(list);
    }

    /**
     * 创建邮箱账户。
     * 先执行连通性验证（IMAP+SMTP），验证失败拒绝创建并返回失败原因（诚实暴露）。
     * 首个账户自动设为默认。
     */
    @PostMapping("/accounts")
    public ApiResult<Map<String, Object>> createAccount(@RequestBody AccountCredentialRequest req) {
        String invalid = req.validate();
        if (invalid != null) {
            return ApiResult.fail(40000, invalid);
        }
        MailAccount account = req.toEntity();
        String verifyError = protocolService.verify(account, req.getPassword());
        if (verifyError != null) {
            return ApiResult.fail(40000, verifyError);
        }
        MailAccount saved = accountService.create(account, req.getPassword());
        return ApiResult.success(accountService.toResponse(saved));
    }

    /** 账户连通性验证（不落库） */
    @PostMapping("/accounts/verify")
    public ApiResult<Map<String, Object>> verifyAccount(@RequestBody AccountCredentialRequest req) {
        String invalid = req.validate();
        if (invalid != null) {
            return ApiResult.fail(40000, invalid);
        }
        String verifyError = protocolService.verify(req.toEntity(), req.getPassword());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", verifyError == null);
        if (verifyError != null) {
            result.put("message", verifyError);
        }
        return ApiResult.success(result);
    }

    /**
     * 局部更新邮箱账户（归属校验；仅更新出现字段，凭据更新时重新 AES-GCM 加密）。
     * 同时接受 PUT 与 PATCH（语义均为部分更新，对齐前端契约缺口记录的 PUT/PATCH 口径）。
     * 不强制连通性验证：编辑常用于修复已失效配置，强制 verify 会把用户锁死；
     * 前端可经 POST /accounts/verify 显式前置验证（与创建流程同端点）。
     * @Transactional：isDefault=true 时「清其他默认 + 本账户置位」为多行写，须原子提交。
     */
    @RequestMapping(value = "/accounts/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<Map<String, Object>> updateAccount(@PathVariable String id,
                                                        @RequestBody MailAccountService.AccountUpdateRequest req) {
        MailAccount account = accountService.getOwned(id);
        if (account == null) {
            return ApiResult.fail(40400, "邮箱账户不存在: " + id);
        }
        if (req.isEmpty()) {
            return ApiResult.fail(40000, "无可更新字段");
        }
        String invalid = req.validate();
        if (invalid != null) {
            return ApiResult.fail(40000, invalid);
        }
        MailAccount updated = accountService.update(account, req);
        return ApiResult.success(accountService.toResponse(updated));
    }

    /**
     * 删除邮箱账户（逻辑删除）：级联逻辑删除其邮件与自定义文件夹；
     * 删除默认账户且仍有剩余账户时，最早创建的剩余账户自动接任默认。
     * 标签/签名/联系人按用户级隔离，不级联（见 MailAccountService.delete 论证）。
     */
    @DeleteMapping("/accounts/{id}")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<Map<String, Object>> deleteAccount(@PathVariable String id) {
        MailAccount account = accountService.getOwned(id);
        if (account == null) {
            return ApiResult.fail(40400, "邮箱账户不存在: " + id);
        }
        Map<String, Integer> cascade = accountService.delete(account);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("cascadeMessages", cascade.get("cascadeMessages"));
        result.put("cascadeFolders", cascade.get("cascadeFolders"));
        return ApiResult.success(result);
    }

    // ==================== 文件夹 ====================

    /** 系统文件夹列表（含未读/总数聚合；important 为 P2 占位，计数为 0） */
    @GetMapping("/folders")
    public ApiResult<List<Map<String, Object>>> folders(@RequestParam(required = false) String accountId) {
        Map<String, long[]> counts = messageService.folderCounts(accountId);
        List<Map<String, Object>> folders = new ArrayList<>();
        folders.add(folder("inbox", "Inbox", "icon-email", counts.get("inbox"), 1));
        folders.add(folder("important", "Important", "icon-user", null, 2));
        folders.add(folder("starred", "Starred", "icon-star", counts.get("starred"), 3));
        folders.add(folder("sent", "Sent", "icon-send", counts.get("sent"), 4));
        folders.add(folder("draft", "Drafts", "icon-edit", counts.get("draft"), 5));
        folders.add(folder("archive", "Archive", "icon-archive", counts.get("archive"), 6));
        folders.add(folder("todo", "Todo", "icon-check-circle", counts.get("todo"), 7));
        folders.add(folder("spam", "Spam", "icon-delete", counts.get("spam"), 8));
        folders.add(folder("trash", "Trash", "icon-delete", counts.get("trash"), 9));
        return ApiResult.success(folders);
    }

    private Map<String, Object> folder(String key, String name, String icon, long[] count, int sortOrder) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", key);
        map.put("name", name);
        map.put("icon", icon);
        map.put("unreadCount", count == null ? 0 : count[0]);
        map.put("totalCount", count == null ? 0 : count[1]);
        map.put("isSystem", true);
        map.put("sortOrder", sortOrder);
        return map;
    }

    // ==================== 同步 ====================

    /**
     * 手动触发 IMAP 收件箱同步（最多拉取 200 封，按 IMAP UID 去重落库）。
     *
     * @param accountId 邮箱账户ID（缺省用默认账户）
     * @return {synced: 新落库封数, total: 本次拉取封数}
     */
    @PostMapping("/sync")
    public ApiResult<Map<String, Object>> sync(@RequestParam(required = false) String accountId) {
        MailAccount account = resolveAccount(accountId);
        if (account == null) {
            return ApiResult.fail(40400, "邮箱账户不存在或未配置: " + accountId);
        }
        String password;
        try {
            password = accountService.decryptPassword(account);
        } catch (IllegalStateException e) {
            // 凭据解密失败（KEK 未配置/密文损坏）— 显式业务码，避免落全局 -2
            // （与 send/source/attachments 同构：服务端凭据状态问题非客户端请求格式错误，HTTP 400 语义不当）
            log.warn("邮箱凭据不可用（account={}）: {}", account.getEmail(), e.getMessage());
            return ApiResult.fail(50000, e.getMessage());
        }
        MailSyncService.SyncResult result;
        try {
            // 与定时同步（MailSyncScheduleTask）共用核心流程：拉取 → UID 去重 → 落库 → 收信过滤器钩子
            result = mailSyncService.syncAccount(account, password);
        } catch (MessagingException e) {
            accountService.markSyncResult(account.getId(), false);
            log.warn("邮件同步失败（account={}）: {}", account.getEmail(), e.getMessage());
            return ApiResult.fail(50200, "收信服务器同步失败: " + rootMessage(e));
        }
        accountService.markSyncResult(account.getId(), true);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("synced", result.synced());
        body.put("total", result.total());
        return ApiResult.success(body);
    }

    // ==================== 邮件 ====================

    /** 邮件分页列表 */
    @GetMapping("/list")
    public ApiResult<Map<String, Object>> list(@RequestParam Map<String, String> query) {
        MailMessageService.MailListParams params = new MailMessageService.MailListParams();
        params.setAccountId(blankToNull(query.get("accountId")));
        params.setFolder(blankToNull(query.get("folder")) == null ? "inbox" : query.get("folder"));
        params.setPage(parseInt(query.get("page"), 1));
        params.setPageSize(parseInt(query.get("pageSize"), 20));
        params.setKeyword(blankToNull(query.get("keyword")));
        params.setFrom(blankToNull(query.get("from")));
        params.setTo(blankToNull(query.get("to")));
        params.setSubject(blankToNull(query.get("subject")));
        params.setIsUnread(parseBool(query.get("isUnread")));
        params.setIsStarred(parseBool(query.get("isStarred")));
        params.setHasAttachment(parseBool(query.get("hasAttachment")));
        params.setLabelId(blankToNull(query.get("labelId")));
        params.setDateFrom(blankToNull(query.get("dateFrom")));
        params.setDateTo(blankToNull(query.get("dateTo")));
        // axios 默认序列化嵌套对象为 sort[field]/sort[order]
        params.setSortField(blankToNull(query.get("sort[field]")));
        params.setSortOrder(blankToNull(query.get("sort[order]")));
        // P1-F4: viewMode=conversation 时按会话归组返回（MailConversationPage 契约）
        params.setViewMode(blankToNull(query.get("viewMode")));
        return ApiResult.success(messageService.list(params));
    }

    /** 邮件详情（归属当前用户校验） */
    @GetMapping("/{id}")
    public ApiResult<Map<String, Object>> detail(@PathVariable String id) {
        MailMessage msg = messageService.getOwned(id);
        if (msg == null) {
            return ApiResult.fail(40400, "邮件不存在: " + id);
        }
        return ApiResult.success(messageService.toResponse(msg));
    }

    /**
     * SMTP 发送邮件并保存发件箱副本（multipart/mixed 真实附件）。
     *
     * <p>附件经 token 解析本地落盘文件随信发出；token 缺失/失效/越权 fail-fast 40000。
     * SMTP 发送失败时留痕发件箱失败副本（send_status='failed' + send_error 摘要，V76），
     * 供 bg-send 状态查询回读与用户重发，响应仍为 50200 失败（诚实暴露）。
     *
     * @return {id: 发件箱副本记录ID}
     */
    @PostMapping("/send")
    public ApiResult<Map<String, Object>> send(@RequestBody MailMessageService.ComposeRequest compose) {
        if (compose.getTo() == null || compose.getTo().isEmpty()) {
            return ApiResult.fail(40000, "收件人不能为空");
        }
        MailAccount account = resolveAccount(compose.getFromAccountId());
        if (account == null) {
            return ApiResult.fail(40400, "邮箱账户不存在或未配置，请先在设置中添加邮箱账户");
        }
        List<MailProtocolService.ResolvedMailFile> files;
        try {
            files = resolveAttachments(compose);
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(40000, e.getMessage());
        }
        String password;
        try {
            password = accountService.decryptPassword(account);
        } catch (IllegalStateException e) {
            // 凭据解密失败（KEK 未配置/密文损坏）— 显式业务码，避免落全局 -2
            log.warn("邮箱凭据不可用（account={}）: {}", account.getEmail(), e.getMessage());
            return ApiResult.fail(50000, e.getMessage());
        }
        MailProtocolService.ComposeMail composeMail = toComposeMail(compose);
        String smtpMessageId;
        try {
            smtpMessageId = protocolService.send(account, password, composeMail, files);
        } catch (MessagingException e) {
            messageService.saveFailedCopy(account, compose, rootMessage(e));
            log.warn("邮件发送失败（account={}）: {}", account.getEmail(), e.getMessage());
            return ApiResult.fail(50200, "发信服务器发送失败: " + rootMessage(e));
        }
        String copyId = messageService.saveSentCopy(account, compose, smtpMessageId);
        // P2-V78 钩子：发送成功后记录最近收件人（to/cc/bcc 全量；辅助数据，失败仅日志不影响发送结果）
        contactRecentService.recordCompose(compose);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", copyId);
        return ApiResult.success(result);
    }

    /** 批量操作 */
    @PostMapping("/batch")
    public ApiResult<Map<String, Object>> batch(@RequestBody BatchRequest req) {
        if (req.getIds() == null || req.getIds().isEmpty()) {
            return ApiResult.fail(40000, "邮件ID列表不能为空");
        }
        if (req.getOp() == null || req.getOp().isBlank()) {
            return ApiResult.fail(40000, "操作类型不能为空");
        }
        int affected;
        try {
            affected = messageService.batch(req.getIds(), req.getOp(), req.getTarget());
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(40000, e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("affected", affected);
        return ApiResult.success(result);
    }

    /** 更新邮件用户备注（V75；归属校验，空串清除备注） */
    @PatchMapping("/{id}/note")
    public ApiResult<Map<String, Object>> updateNote(@PathVariable String id, @RequestBody NoteRequest req) {
        MailMessage msg = messageService.getOwned(id);
        if (msg == null) {
            return ApiResult.fail(40400, "邮件不存在: " + id);
        }
        messageService.updateNote(msg, req.getNote());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        return ApiResult.success(result);
    }

    // ==================== 源码 / 撤回 / 发送状态（P1 第二批，V76） ====================

    /**
     * 邮件源码查看（V76）：IMAP 按 UID 回源完整 RFC822 报文，返回原始源码 + 解析后的邮件头。
     *
     * <p>fail-fast 语义：本地发件副本/草稿无服务器原件（40400）；UID 在服务器已失效（40400）；
     * 服务器连接/认证失败（50200）；已取回字节本地解析失败（50000）。禁止返回空串兜底。
     *
     * @return {rawSource: 原始报文（严格 UTF-8 优先、ISO-8859-1 无损兜底，见 MailMimeSupport.decodeRawSource）, headers: 有序邮件头（同名头取首值）}
     */
    @GetMapping("/{id}/source")
    public ApiResult<Map<String, Object>> source(@PathVariable String id) {
        MailMessage msg = messageService.getOwned(id);
        if (msg == null) {
            return ApiResult.fail(40400, "邮件不存在: " + id);
        }
        if (msg.getImapUid() == null || msg.getImapUid().isBlank()) {
            return ApiResult.fail(40400, "该邮件为本地发件副本/草稿，无服务器原件可回源查看源码");
        }
        MailAccount account = accountService.getOwned(msg.getAccountId());
        if (account == null) {
            return ApiResult.fail(40400, "邮件所属邮箱账户不存在或已删除");
        }
        byte[] raw;
        try {
            raw = protocolService.fetchRawMessage(account, accountService.decryptPassword(account), msg.getImapUid());
        } catch (MessagingException e) {
            log.warn("邮件源码回源失败（account={}, uid={}）: {}", account.getEmail(), msg.getImapUid(), e.getMessage());
            return ApiResult.fail(50200, "邮件服务器回源失败: " + rootMessage(e));
        } catch (IllegalStateException e) {
            // 凭据解密失败（KEK 未配置/密文损坏）— 显式业务码，避免落全局 -2
            log.warn("邮箱凭据不可用（account={}）: {}", account.getEmail(), e.getMessage());
            return ApiResult.fail(50000, e.getMessage());
        }
        if (raw == null) {
            return ApiResult.fail(40400, "邮件已从服务器删除或移动，无法获取源码");
        }
        Map<String, String> headers;
        try {
            MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()),
                    new ByteArrayInputStream(raw));
            headers = MailMimeSupport.headerMap(mime);
        } catch (MessagingException e) {
            log.warn("邮件源码解析失败（id={}）: {}", id, e.getMessage());
            return ApiResult.fail(50000, "邮件源码解析失败: " + rootMessage(e));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rawSource", MailMimeSupport.decodeRawSource(raw));
        result.put("headers", headers);
        return ApiResult.success(result);
    }

    /**
     * 撤回已发送邮件（V76）。
     *
     * <p>诚实实现决策：SMTP/IMAP 协议级无撤回能力（邮件一经 SMTP 投递即脱离本系统控制），
     * 真实撤回仅服务商级能力可提供（Exchange/腾讯企业邮同域召回等，需专有 API，本批次不接入）。
     * 故本端点对发件箱邮件的撤回尝试统一判定为失败：withdraw_status='failed' 落库留痕 +
     * 响应 success:false（HTTP 200，契约 {success, withdrawStatus}，见前端 withdrawMailAction
     * P1-8 分支设计）；不返回 50901 业务错误码，避免拦截器 throw 绕过前端失败态 UI 状态机。
     * 非发件箱邮件/草稿的撤回请求属非法操作，返回 40000。
     */
    @PostMapping("/{id}/withdraw")
    public ApiResult<Map<String, Object>> withdraw(@PathVariable String id) {
        MailMessage msg = messageService.getOwned(id);
        if (msg == null) {
            return ApiResult.fail(40400, "邮件不存在: " + id);
        }
        if (!"sent".equals(msg.getFolder()) || msg.getIsDraft() == 1) {
            return ApiResult.fail(40000, "仅发件箱中已发送的邮件可尝试撤回");
        }
        if (msg.getWithdrawStatus() == null) {
            // 首次撤回尝试：留痕失败事实（SMTP 无撤回能力，见方法级决策注释）
            messageService.markWithdrawFailed(msg);
            msg.setWithdrawStatus("failed");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("withdrawStatus", msg.getWithdrawStatus());
        result.put("message", "当前邮件协议（SMTP）不支持撤回已发送邮件，仅服务商级同域召回能力可提供；本次撤回尝试已记录");
        return ApiResult.success(result);
    }

    /**
     * 后台发送状态查询（V76）：taskId 为 POST /send 返回的发件箱副本 id。
     *
     * <p>当前发送为同步 SMTP 直发，状态列仅存 sent/failed 终态；queued/sending 中间态
     * 预留给未来异步发送队列（列定义已兼容，无需再迁移）。
     *
     * @return {status: queued/sending/sent/failed, progress: 0/50/100}
     */
    @GetMapping("/bg-send/{taskId}")
    public ApiResult<Map<String, Object>> bgSendStatus(@PathVariable String taskId) {
        MailMessage msg = messageService.getOwned(taskId);
        if (msg == null) {
            return ApiResult.fail(40400, "发送任务不存在: " + taskId);
        }
        String status = msg.getSendStatus();
        if (status == null || status.isBlank()) {
            return ApiResult.fail(40400, "该邮件无发送状态（非发件副本）: " + taskId);
        }
        int progress = switch (status) {
            case "sent", "failed" -> 100;
            case "sending" -> 50;
            default -> 0;
        };
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("progress", progress);
        return ApiResult.success(result);
    }

    // ==================== 附件（P1 第二批，V76） ====================

    /**
     * 附件真实上传（multipart/form-data，字段名 file）。
     *
     * <p>文件落 {@code GEELATO_UPLOAD_ROOT_DIRECTORY/mail-attachments/{userId}/{yyyyMM}/{uuid}}，
     * 按用户隔离；单文件 ≤20MB（业务级校验，全局兜底 25MB 见 MailMultipartConfig）。
     * 返回的 token 随写信表单 attachments[].token 提交，发送时解析落盘文件随信发出。
     *
     * @return {token, name, size, contentType, type, status:'uploaded'}
     */
    @PostMapping("/attachments/upload")
    public ApiResult<Map<String, Object>> uploadAttachment(@RequestParam("file") MultipartFile file) {
        MailAttachmentStorageService.StoredAttachment stored;
        try {
            stored = attachmentStorageService.store(file, MailSessionCtx.getCurrentUserId());
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(40000, e.getMessage());
        } catch (IOException e) {
            log.warn("附件写入存储失败: {}", e.getMessage());
            return ApiResult.fail(50000, "附件写入存储失败: " + e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", stored.token());
        result.put("name", stored.originalName());
        // JacksonConfig 全局 Long→String（防雪花溢出）；附件大小 ≤20MB 用 int 输出为 JSON number
        result.put("size", (int) stored.size());
        result.put("contentType", stored.contentType());
        result.put("type", MailMimeSupport.attachmentType(stored.contentType(), stored.originalName()));
        result.put("status", "uploaded");
        return ApiResult.success(result);
    }

    /**
     * 附件内容下载：GET /api/mail/{mailId}/attachments/{index}（复合引用 {mailId}:{index} 的 REST 形态）。
     *
     * <p>解析顺序：attachments_json 元数据带 token（本端上传的真实附件，发件副本/草稿）
     * 优先读本地落盘文件；无 token（IMAP 同步的收件箱邮件，仅有元数据）按 UID 回源
     * IMAP 整封拉取后按同口径下标提取附件内容（ContentExtractor/MailMimeSupport 同序保证）。
     *
     * <p>错误语义：记录/下标/token 文件不存在 → HTTP 404 + 40400；服务器回源失败 →
     * HTTP 502 + 50200；成功 → 附件字节流（Content-Type + Content-Disposition RFC 6266 双文件名）。
     */
    @GetMapping("/{mailId}/attachments/{index}")
    public ResponseEntity<Object> downloadAttachment(@PathVariable String mailId, @PathVariable int index) {
        MailMessage msg = messageService.getOwned(mailId);
        if (msg == null) {
            return downloadError(HttpStatus.NOT_FOUND, 40400, "邮件不存在: " + mailId);
        }
        List<Map<String, Object>> metadata = messageService.attachmentMetadata(msg);
        if (index < 0 || index >= metadata.size()) {
            return downloadError(HttpStatus.NOT_FOUND, 40400,
                    "附件不存在: " + mailId + " 下标 " + index + " 越界（共 " + metadata.size() + " 个）");
        }
        Map<String, Object> item = metadata.get(index);
        String name = item.get("name") == null ? "attachment" : String.valueOf(item.get("name"));
        Object token = item.get("token");
        if (token != null && !String.valueOf(token).isBlank()) {
            return downloadLocal(String.valueOf(token), name, item.get("contentType"));
        }
        return downloadFromImap(msg, index, name);
    }

    /** 本地落盘附件下载（token 归属 + 防穿越由 MailAttachmentStorageService.resolve 保证） */
    private ResponseEntity<Object> downloadLocal(String token, String name, Object contentTypeMeta) {
        MailAttachmentStorageService.ResolvedAttachment resolved =
                attachmentStorageService.resolve(token, MailSessionCtx.getCurrentUserId());
        if (resolved == null) {
            return downloadError(HttpStatus.NOT_FOUND, 40400, "附件文件不存在或已清理: " + name);
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(resolved.path());
        } catch (IOException e) {
            log.warn("附件读取失败（name={}）: {}", name, e.getMessage());
            return downloadError(HttpStatus.INTERNAL_SERVER_ERROR, 50000, "附件读取失败: " + e.getMessage());
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeContentType(contentTypeMeta)))
                .header(HttpHeaders.CONTENT_DISPOSITION, MailMimeSupport.contentDisposition(name))
                .contentLength(bytes.length)
                .body(bytes);
    }

    /** IMAP 回源附件下载（收件箱同步邮件；整封拉取 + 同口径下标提取） */
    private ResponseEntity<Object> downloadFromImap(MailMessage msg, int index, String name) {
        if (msg.getImapUid() == null || msg.getImapUid().isBlank()) {
            return downloadError(HttpStatus.NOT_FOUND, 40400,
                    "该邮件为本地副本且无附件落盘凭证，无法下载: " + name);
        }
        MailAccount account = accountService.getOwned(msg.getAccountId());
        if (account == null) {
            return downloadError(HttpStatus.NOT_FOUND, 40400, "邮件所属邮箱账户不存在或已删除");
        }
        MailProtocolService.FetchedAttachment fetched;
        try {
            fetched = protocolService.fetchAttachment(
                    account, accountService.decryptPassword(account), msg.getImapUid(), index);
        } catch (MessagingException e) {
            log.warn("附件回源失败（account={}, uid={}, index={}）: {}",
                    account.getEmail(), msg.getImapUid(), index, e.getMessage());
            return downloadError(HttpStatus.BAD_GATEWAY, 50200, "邮件服务器回源失败: " + rootMessage(e));
        } catch (IllegalStateException e) {
            // 凭据解密失败（KEK 未配置/密文损坏）— 显式业务码，避免落全局 -2
            log.warn("邮箱凭据不可用（account={}）: {}", account.getEmail(), e.getMessage());
            return downloadError(HttpStatus.INTERNAL_SERVER_ERROR, 50000, e.getMessage());
        }
        if (fetched == null) {
            return downloadError(HttpStatus.NOT_FOUND, 40400, "附件不存在或邮件已从服务器移除: " + name);
        }
        String fileName = fetched.fileName() == null || fetched.fileName().isBlank()
                ? name : fetched.fileName();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(fetched.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, MailMimeSupport.contentDisposition(fileName))
                .contentLength(fetched.content().length)
                .body(fetched.content());
    }

    /** 下载端点错误响应（HTTP 语义状态码 + JSON 业务体，供浏览器直连/拦截器双通道识别） */
    private ResponseEntity<Object> downloadError(HttpStatus status, int code, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResult.fail(code, message));
    }

    // ==================== 内部辅助 ====================

    /**
     * 解析写信附件 token 为本地落盘文件（发送用）。
     * token 缺失/非法/越权/文件丢失一律 fail-fast IllegalArgumentException（调用方转 40000），
     * 禁止静默跳过附件假装发送成功。
     */
    private List<MailProtocolService.ResolvedMailFile> resolveAttachments(MailMessageService.ComposeRequest compose) {
        if (compose.getAttachments() == null || compose.getAttachments().isEmpty()) {
            return List.of();
        }
        String userId = MailSessionCtx.getCurrentUserId();
        List<MailProtocolService.ResolvedMailFile> files = new ArrayList<>();
        for (MailMessageService.AttachmentDto att : compose.getAttachments()) {
            String name = MailMimeSupport.sanitizeFileName(att.getName());
            if (att.getToken() == null || att.getToken().isBlank()) {
                throw new IllegalArgumentException("附件缺少上传凭证（token），请重新上传: " + name);
            }
            MailAttachmentStorageService.ResolvedAttachment resolved =
                    attachmentStorageService.resolve(att.getToken(), userId);
            if (resolved == null) {
                throw new IllegalArgumentException("附件已失效或不属于当前用户，请重新上传: " + name);
            }
            files.add(new MailProtocolService.ResolvedMailFile(
                    name, mimeContentType(att.getContentType()), resolved.path()));
        }
        return files;
    }

    /** 元数据 Content-Type 归一化：非 MIME 形态（如前端 type 枚举 pdf/image）按 octet-stream 兜底 */
    private String mimeContentType(Object contentType) {
        String ct = contentType == null ? "" : String.valueOf(contentType).trim();
        if (ct.isEmpty() || !ct.contains("/")) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        return ct;
    }

    /** 解析目标账户：显式 id → 默认账户 → 唯一账户；均无返回 null */
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

    private MailProtocolService.ComposeMail toComposeMail(MailMessageService.ComposeRequest req) {
        return new MailProtocolService.ComposeMail(
                toProtocolAddresses(req.getTo()),
                toProtocolAddresses(req.getCc()),
                toProtocolAddresses(req.getBcc()),
                req.getSubject(),
                req.getContent(),
                req.getInReplyTo());
    }

    private List<MailProtocolService.ComposeMail.MailAddress> toProtocolAddresses(
            List<MailMessageService.AddressDto> list) {
        if (list == null) {
            return List.of();
        }
        return list.stream()
                .filter(a -> a != null && a.getEmail() != null && !a.getEmail().isBlank())
                .map(a -> new MailProtocolService.ComposeMail.MailAddress(a.getName(), a.getEmail()))
                .collect(Collectors.toList());
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private int parseInt(String s, int defaultValue) {
        if (s == null || s.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Boolean parseBool(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return "true".equalsIgnoreCase(s.trim()) || "1".equals(s.trim());
    }

    private String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    // ==================== DTO ====================

    /** 批量操作请求（与前端 MailBatchOp 对齐；setLabels 时 target 为逗号分隔标签ID） */
    @lombok.Data
    public static class BatchRequest {
        private List<String> ids;
        private String op;
        private String target;
        private String accountId;
    }

    /** 备注更新请求（与前端 updateMailNote 对齐） */
    @lombok.Data
    public static class NoteRequest {
        private String note;
    }

    /** 账户凭据请求（与前端 MailAccountCredential 对齐） */
    @lombok.Data
    public static class AccountCredentialRequest {
        private String email;
        private String name;
        private String providerCode;
        private String password;
        private Servers servers;

        /** 基础必填校验；返回错误信息，null 表示通过 */
        public String validate() {
            if (email == null || email.isBlank()) {
                return "邮箱地址不能为空";
            }
            if (password == null || password.isBlank()) {
                return "邮箱密码/授权码不能为空";
            }
            if (servers == null || servers.getIncoming() == null || servers.getOutgoing() == null) {
                return "服务器配置不完整";
            }
            if (servers.getIncoming().getHost() == null || servers.getIncoming().getHost().isBlank()) {
                return "收信服务器主机不能为空";
            }
            if (servers.getOutgoing().getHost() == null || servers.getOutgoing().getHost().isBlank()) {
                return "发信服务器主机不能为空";
            }
            return null;
        }

        /** 转 MailAccount 实体（不含审计字段/凭据密文，由 Service 层补齐） */
        public MailAccount toEntity() {
            MailAccount account = new MailAccount();
            account.setEmail(email.trim());
            account.setName(name == null || name.isBlank() ? email.trim() : name.trim());
            account.setProviderCode(providerCode);
            account.setUsername(email.trim());
            account.setIncomingProtocol(
                    servers.getIncoming().getProtocol() == null ? "imap" : servers.getIncoming().getProtocol());
            account.setIncomingHost(servers.getIncoming().getHost().trim());
            account.setIncomingPort(servers.getIncoming().getPort());
            account.setIncomingEncryption(
                    servers.getIncoming().getEncryption() == null ? "ssl" : servers.getIncoming().getEncryption());
            account.setOutgoingHost(servers.getOutgoing().getHost().trim());
            account.setOutgoingPort(servers.getOutgoing().getPort());
            account.setOutgoingEncryption(
                    servers.getOutgoing().getEncryption() == null ? "ssl" : servers.getOutgoing().getEncryption());
            return account;
        }

        @lombok.Data
        public static class Servers {
            private Incoming incoming;
            private Outgoing outgoing;
        }

        @lombok.Data
        public static class Incoming {
            private String protocol;
            private String host;
            private int port;
            private String encryption;
        }

        @lombok.Data
        public static class Outgoing {
            private String host;
            private int port;
            private String encryption;
        }
    }
}
