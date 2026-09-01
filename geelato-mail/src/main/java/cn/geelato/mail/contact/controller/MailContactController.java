package cn.geelato.mail.contact.controller;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import cn.geelato.mail.contact.entity.MailContact;
import cn.geelato.mail.contact.service.MailContactGroupService;
import cn.geelato.mail.contact.service.MailContactImportService;
import cn.geelato.mail.contact.service.MailContactRecentService;
import cn.geelato.mail.contact.service.MailContactService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 邮件联系人 Controller（P2-V78，矩阵附录 A P2 清单 15 端点中的联系人侧 11 个 +
 * 契约缺口 suggest + 最近收件人查询/清除）。
 *
 * 由 ApiPrefixAutoConfiguration 自动加 /api 前缀。实际路径 = /api/mail/contacts。
 *
 * 端点列表：
 * - GET    /contacts?keyword&groupId&accountId&page&pageSize  分页列表（服务端分页）
 * - GET    /contacts/{id}                                     详情（含 mailCount/lastContactAt 聚合）
 * - POST   /contacts                                          创建（name/email 必填，邮箱去重 fail-fast）
 * - PATCH  /contacts/{id}                                     局部更新（归属校验）
 * - DELETE /contacts/{id}                                     逻辑删除（归属校验）
 * - POST   /contacts/batch-delete                             批量逻辑删除 {ids}
 * - POST   /contacts/merge                                    合并 {primaryId, secondaryIds}（事务性）
 * - GET    /contacts/{id}/history                             来往邮件历史（mail_message 实时推导）
 * - POST   /contacts/import                                   导入（multipart file，csv/vcf）
 * - GET    /contacts/export?format=csv|vcf                    导出（当前用户全量）
 * - GET    /contacts/suggest?q&limit                          收件人联想（契约缺口，compose TODO P1-F5）
 * - GET    /contacts/recent?limit                             最近收件人查询
 * - DELETE /contacts/recent                                   最近收件人清除
 *
 * 数据隔离：全部按当前登录用户 userId 过滤；写操作做归属校验。
 * groupId 入参约定："0"=未分组（置 NULL）；缺失=不过滤/不改动。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@ApiRuntimeRestController("/mail/contacts")
public class MailContactController {

    @Autowired
    private MailContactService contactService;

    @Autowired
    private MailContactGroupService groupService;

    @Autowired
    private MailContactRecentService recentService;

    @Autowired
    private MailContactImportService importService;

    /** 分页列表（MailListPage 契约：{list,total,page,pageSize,hasMore}） */
    @GetMapping
    public ApiResult<Map<String, Object>> list(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String groupId,
                                               @RequestParam(required = false) String accountId,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResult.success(contactService.list(keyword, groupId, accountId, page, pageSize));
    }

    /** 详情（含来往邮件聚合统计） */
    @GetMapping("/{id}")
    public ApiResult<Map<String, Object>> detail(@PathVariable String id) {
        MailContact contact = contactService.getOwned(id);
        if (contact == null) {
            return ApiResult.fail(40400, "联系人不存在: " + id);
        }
        return ApiResult.success(contactService.detail(contact));
    }

    /** 创建联系人（name/email 必填；邮箱格式 + 去重校验 fail-fast） */
    @PostMapping
    public ApiResult<Map<String, Object>> create(@RequestBody ContactRequest req) {
        if (req.getName() == null || req.getName().isBlank()) {
            return ApiResult.fail(40000, "联系人姓名不能为空");
        }
        if (req.getEmail() == null || req.getEmail().isBlank()) {
            return ApiResult.fail(40000, "联系人邮箱不能为空");
        }
        String groupId = normalizeGroupId(req.getGroupId());
        if (groupId != null && groupService.getOwned(groupId) == null) {
            return ApiResult.fail(40000, "分组不存在: " + groupId);
        }
        MailContact contact;
        try {
            contact = contactService.create(req.getName(), req.getEmail(), req.getPhone(), req.getOrg(),
                    req.getAvatar(), req.getNotes(), groupId, req.getAccountId());
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(40000, e.getMessage());
        }
        return ApiResult.success(contactService.toResponse(contact, null));
    }

    /** 局部更新（仅更新出现字段；空串/"0" 清除分组置未分组） */
    @PatchMapping("/{id}")
    public ApiResult<Map<String, Object>> update(@PathVariable String id, @RequestBody ContactRequest req) {
        MailContact contact = contactService.getOwned(id);
        if (contact == null) {
            return ApiResult.fail(40400, "联系人不存在: " + id);
        }
        // groupId 缺席=不改动；出现但为空/"0"=清除（传空串由 Service 置 NULL）；否则校验归属
        boolean groupTouched = req.getGroupId() != null;
        String groupId = normalizeGroupId(req.getGroupId());
        if (groupTouched && groupId != null && groupService.getOwned(groupId) == null) {
            return ApiResult.fail(40000, "分组不存在: " + groupId);
        }
        try {
            contactService.update(contact, req.getName(), req.getEmail(), req.getPhone(), req.getOrg(),
                    req.getAvatar(), req.getNotes(), groupTouched ? (groupId == null ? "" : groupId) : null);
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(40000, e.getMessage());
        }
        return ApiResult.success(successResult());
    }

    /** 逻辑删除 */
    @DeleteMapping("/{id}")
    public ApiResult<Map<String, Object>> delete(@PathVariable String id) {
        MailContact contact = contactService.getOwned(id);
        if (contact == null) {
            return ApiResult.fail(40400, "联系人不存在: " + id);
        }
        contactService.delete(contact);
        return ApiResult.success(successResult());
    }

    /** 批量逻辑删除 {ids}（越权/不存在 id 跳过，返回实际删除条数） */
    @PostMapping("/batch-delete")
    public ApiResult<Map<String, Object>> batchDelete(@RequestBody BatchDeleteRequest req) {
        if (req.getIds() == null || req.getIds().isEmpty()) {
            return ApiResult.fail(40000, "联系人ID列表不能为空");
        }
        int affected = contactService.batchDelete(req.getIds());
        Map<String, Object> result = successResult();
        result.put("affected", affected);
        return ApiResult.success(result);
    }

    /**
     * 合并联系人 {primaryId, secondaryIds}：次要联系人非空白字段回填主联系人空白字段后删除。
     * 任一次要联系人不存在/越权则整体不写入，返回 {success:false, failedIds}（与 mock 契约一致）。
     */
    @PostMapping("/merge")
    public ApiResult<Map<String, Object>> merge(@RequestBody MergeRequest req) {
        if (req.getPrimaryId() == null || req.getPrimaryId().isBlank()) {
            return ApiResult.fail(40000, "主联系人ID不能为空");
        }
        if (req.getSecondaryIds() == null || req.getSecondaryIds().isEmpty()) {
            return ApiResult.fail(40000, "次要联系人ID列表不能为空");
        }
        if (req.getSecondaryIds().contains(req.getPrimaryId())) {
            return ApiResult.fail(40000, "次要联系人不能包含主联系人自身: " + req.getPrimaryId());
        }
        MailContact primary = contactService.getOwned(req.getPrimaryId());
        if (primary == null) {
            return ApiResult.fail(40400, "主联系人不存在: " + req.getPrimaryId());
        }
        List<String> failedIds = contactService.merge(primary, req.getSecondaryIds());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", failedIds.isEmpty());
        if (!failedIds.isEmpty()) {
            result.put("failedIds", failedIds);
        }
        return ApiResult.success(result);
    }

    /** 来往邮件历史（mail_message 实时推导，上限 50 条按发送时间倒序） */
    @GetMapping("/{id}/history")
    public ApiResult<List<Map<String, Object>>> history(@PathVariable String id) {
        MailContact contact = contactService.getOwned(id);
        if (contact == null) {
            return ApiResult.fail(40400, "联系人不存在: " + id);
        }
        return ApiResult.success(contactService.history(contact));
    }

    /**
     * 导入联系人（multipart file；csv/vcf/vcard，≤10MB，≤2000 条）。
     * 结构性错误 fail-fast 40000（消息含行号）；数据级错误计 failed 并附 failures 行号明细。
     *
     * @return {success: 成功条数, failed: 失败条数, failures: [{line,email,reason}]}
     */
    @PostMapping("/import")
    public ApiResult<Map<String, Object>> importContacts(@RequestParam("file") MultipartFile file) {
        MailContactImportService.ImportOutcome outcome;
        try {
            outcome = importService.importContacts(file.getOriginalFilename(), file.getBytes());
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(40000, e.getMessage());
        } catch (IOException e) {
            return ApiResult.fail(40000, "导入文件读取失败: " + e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", outcome.success);
        result.put("failed", outcome.failed);
        if (!outcome.failures.isEmpty()) {
            result.put("failures", outcome.failures);
        }
        return ApiResult.success(result);
    }

    /**
     * 导出当前用户全量联系人（format=csv 默认 | vcf）。
     * 成功返回原始文本（非 ApiResult 包络，与前端 responseType:'text' 契约及 swf export 先例一致）；
     * 失败（不支持的格式）走全局异常前的 ApiResult.fail 包络——该分支仅当前端绕过类型约束时可达。
     */
    @GetMapping("/export")
    public Object exportContacts(@RequestParam(defaultValue = "csv") String format) {
        List<MailContact> contacts = contactService.listEntities(null);
        if ("csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                    .body(importService.exportCsv(contacts));
        }
        if ("vcf".equalsIgnoreCase(format) || "vcard".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/vcard;charset=UTF-8"))
                    .body(importService.exportVcf(contacts));
        }
        return ApiResult.fail(40000, "不支持的导出格式（仅支持 csv/vcf）: " + format);
    }

    /**
     * 收件人联想（契约缺口补建：compose TODO P1-F5）。
     * 联系人（name/email 前缀匹配）优先，最近收件人补齐；按 lower(email) 去重。
     *
     * @return [{id,name,email,source:contact|recent}]；recent 条目 id 为 null
     */
    @GetMapping("/suggest")
    public ApiResult<List<Map<String, Object>>> suggest(@RequestParam(required = false) String q,
                                                        @RequestParam(defaultValue = "10") int limit) {
        return ApiResult.success(contactService.suggest(q, limit));
    }

    /** 最近收件人查询（按最近使用倒序；limit 默认 50） */
    @GetMapping("/recent")
    public ApiResult<List<Map<String, Object>>> recent(@RequestParam(defaultValue = "50") int limit) {
        return ApiResult.success(recentService.listRecent(limit));
    }

    /** 最近收件人清除（当前用户全量逻辑删除） */
    @DeleteMapping("/recent")
    public ApiResult<Map<String, Object>> clearRecent() {
        int cleared = recentService.clear();
        Map<String, Object> result = successResult();
        result.put("cleared", cleared);
        return ApiResult.success(result);
    }

    // ==================== 内部辅助 ====================

    /** groupId 归一：空串/"0" → null（未分组） */
    private static String normalizeGroupId(String groupId) {
        if (groupId == null || groupId.isBlank() || "0".equals(groupId.trim())) {
            return null;
        }
        return groupId.trim();
    }

    private static Map<String, Object> successResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        return result;
    }

    /** 联系人请求（与前端 createContact/updateContact 对齐；Jackson 标量强制转换兼容 number 型 id） */
    @lombok.Data
    public static class ContactRequest {
        private String name;
        private String email;
        private String phone;
        private String org;
        private String avatar;
        private String notes;
        private String groupId;
        private String accountId;
    }

    /** 批量删除请求 */
    @lombok.Data
    public static class BatchDeleteRequest {
        private List<String> ids;
    }

    /** 合并请求 */
    @lombok.Data
    public static class MergeRequest {
        private String primaryId;
        private List<String> secondaryIds;
    }
}
