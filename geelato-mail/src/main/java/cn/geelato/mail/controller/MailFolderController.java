package cn.geelato.mail.controller;

import cn.geelato.core.orm.Dao;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.query.Filter;
import cn.geelato.orm.query.MetaQuery;
import cn.geelato.orm.query.Order;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import cn.geelato.mail.entity.MailAccount;
import cn.geelato.mail.entity.MailFolderCustom;
import cn.geelato.mail.entity.MailMessage;
import cn.geelato.mail.service.MailAccountService;
import cn.geelato.mail.service.MailMessageService;
import cn.geelato.mail.util.MailSessionCtx;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 邮件自定义文件夹 Controller（P0，4 个端点）。
 *
 * 由 ApiPrefixAutoConfiguration 自动加 /api 前缀。实际路径 = /api/mail/custom-folders。
 *
 * 端点列表：
 * - GET    /custom-folders?accountId   当前用户自定义文件夹列表
 * - POST   /custom-folders             创建（accountId 缺省落默认账户）
 * - PATCH  /custom-folders/{id}        重命名/调整排序/调整父级
 * - DELETE /custom-folders/{id}        逻辑删除（夹内邮件移回收件箱）
 *
 * 数据隔离：全部按当前登录用户 userId 过滤；删除/修改做归属校验。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@ApiRuntimeRestController("/mail/custom-folders")
public class MailFolderController {

    @Autowired
    @Qualifier("dynamicDao")
    private Dao dynamicDao;

    @Autowired
    private MailAccountService accountService;

    /** 当前用户自定义文件夹列表（按 sortOrder/创建时间排序） */
    @GetMapping
    public ApiResult<List<Map<String, Object>>> list(@RequestParam(required = false) String accountId) {
        List<Filter> filters = new ArrayList<>();
        filters.add(Filter.eq("userId", MailSessionCtx.getCurrentUserId()));
        filters.add(Filter.eq("delStatus", 0));
        if (accountId != null && !accountId.isBlank()) {
            filters.add(Filter.eq("accountId", accountId));
        }
        MetaQuery query = MetaFactory.query(MailFolderCustom.class)
                .where(filters.toArray(new Filter[0]))
                .order(Order.asc("sortOrder"), Order.asc("createAt"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        List<Map<String, Object>> result = rows.stream().map(this::toResponse).toList();
        return ApiResult.success(result);
    }

    /** 创建自定义文件夹（sortOrder 取当前最大值 + 1） */
    @PostMapping
    public ApiResult<Map<String, Object>> create(@RequestBody FolderRequest req) {
        if (req.getName() == null || req.getName().isBlank()) {
            return ApiResult.fail(40000, "文件夹名称不能为空");
        }
        String accountId = resolveAccountId(req.getAccountId());
        if (accountId == null) {
            return ApiResult.fail(40000, "请先配置邮箱账户后再创建文件夹");
        }
        String userId = MailSessionCtx.getCurrentUserId();
        String userName = MailSessionCtx.getCurrentUserName();
        Date now = new Date();

        MailFolderCustom folder = new MailFolderCustom();
        folder.setAccountId(accountId);
        folder.setUserId(userId);
        folder.setName(req.getName().trim());
        folder.setParentId(req.getParentId());
        folder.setSortOrder(nextSortOrder(userId, accountId));
        folder.setTenantCode(MailSessionCtx.getCurrentTenantCode());
        folder.setDelStatus(0);
        folder.setCreateAt(now);
        folder.setUpdateAt(now);
        folder.setCreator(userId);
        folder.setCreatorName(userName);
        folder.setUpdater(userId);
        folder.setUpdaterName(userName);
        Map<String, Object> saved = dynamicDao.save(folder);
        if (folder.getId() == null && saved != null && saved.get("id") != null) {
            folder.setId(String.valueOf(saved.get("id")));
        }
        return ApiResult.success(toResponse(folder));
    }

    /** 更新自定义文件夹（重命名/排序/父级；仅允许操作本人文件夹） */
    @PatchMapping("/{id}")
    public ApiResult<Map<String, Object>> update(@PathVariable String id, @RequestBody FolderRequest req) {
        MailFolderCustom folder = getOwned(id);
        if (folder == null) {
            return ApiResult.fail(40400, "自定义文件夹不存在: " + id);
        }
        if (req.getName() != null && !req.getName().isBlank()) {
            folder.setName(req.getName().trim());
        }
        if (req.getParentId() != null) {
            folder.setParentId(req.getParentId());
        }
        if (req.getSortOrder() != null) {
            folder.setSortOrder(req.getSortOrder());
        }
        folder.setUpdateAt(new Date());
        folder.setUpdater(MailSessionCtx.getCurrentUserId());
        folder.setUpdaterName(MailSessionCtx.getCurrentUserName());
        dynamicDao.save(folder);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        return ApiResult.success(result);
    }

    /**
     * 删除自定义文件夹（逻辑删除），夹内邮件移回收件箱。
     * 物理删除会导致 mail_message.folder = custom_{id} 成为悬空引用，故同步迁移邮件。
     */
    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<Map<String, Object>> delete(@PathVariable String id) {
        MailFolderCustom folder = getOwned(id);
        if (folder == null) {
            return ApiResult.fail(40400, "自定义文件夹不存在: " + id);
        }
        String userId = MailSessionCtx.getCurrentUserId();
        String userName = MailSessionCtx.getCurrentUserName();
        Date now = new Date();

        // 1. 夹内邮件移回收件箱
        MetaQuery moveQuery = MetaFactory.query(MailMessage.class)
                .where(Filter.eq("userId", userId),
                        Filter.eq("folder", "custom_" + id),
                        Filter.eq("delStatus", 0));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mails = moveQuery.list();
        for (Map<String, Object> row : mails) {
            Object mailId = row.get("id");
            if (mailId == null) {
                continue;
            }
            MailMessage msg = dynamicDao.queryForObject(MailMessage.class, String.valueOf(mailId));
            if (msg == null) {
                continue;
            }
            msg.setFolder("inbox");
            msg.setUpdateAt(now);
            msg.setUpdater(userId);
            msg.setUpdaterName(userName);
            dynamicDao.save(msg);
        }

        // 2. 逻辑删除文件夹
        folder.setDelStatus(1);
        folder.setDeleteAt(now);
        folder.setUpdateAt(now);
        folder.setUpdater(userId);
        folder.setUpdaterName(userName);
        dynamicDao.save(folder);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        return ApiResult.success(result);
    }

    // ==================== 内部辅助 ====================

    /** 查询并校验归属当前用户（越权/不存在返回 null） */
    private MailFolderCustom getOwned(String id) {
        MetaQuery query = MetaFactory.query(MailFolderCustom.class)
                .where(Filter.eq("id", id),
                        Filter.eq("userId", MailSessionCtx.getCurrentUserId()),
                        Filter.eq("delStatus", 0));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        return toEntity(rows.get(0));
    }

    /** 解析目标账户 id：显式传入 → 默认账户 → 唯一账户；均无返回 null */
    private String resolveAccountId(String accountId) {
        if (accountId != null && !accountId.isBlank()) {
            MailAccount owned = accountService.getOwned(accountId);
            return owned == null ? null : owned.getId();
        }
        List<MailAccount> accounts = accountService.listByCurrentUser();
        for (MailAccount a : accounts) {
            if (a.getIsDefault() == 1) {
                return a.getId();
            }
        }
        return accounts.size() == 1 ? accounts.get(0).getId() : null;
    }

    private int nextSortOrder(String userId, String accountId) {
        MetaQuery query = MetaFactory.query(MailFolderCustom.class)
                .where(Filter.eq("userId", userId),
                        Filter.eq("accountId", accountId),
                        Filter.eq("delStatus", 0));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        int max = 0;
        for (Map<String, Object> row : rows) {
            Object v = row.get("sortOrder");
            if (v instanceof Number n) {
                max = Math.max(max, n.intValue());
            }
        }
        return max + 1;
    }

    /** 转前端 MailCustomFolder 契约 */
    private Map<String, Object> toResponse(MailFolderCustom folder) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", folder.getId());
        map.put("name", folder.getName());
        if (folder.getParentId() != null) {
            map.put("parentId", folder.getParentId());
        }
        map.put("sortOrder", folder.getSortOrder());
        map.put("createdAt", folder.getCreateAt() == null ? "" : folder.getCreateAt().toInstant().toString());
        return map;
    }

    private Map<String, Object> toResponse(Map<String, Object> row) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", str(row.get("id")));
        map.put("name", str(row.get("name")));
        Object parentId = row.get("parentId");
        if (parentId != null) {
            map.put("parentId", String.valueOf(parentId));
        }
        Object sortOrder = row.get("sortOrder");
        map.put("sortOrder", sortOrder instanceof Number n ? n.intValue() : 0);
        Object createAt = row.get("createAt");
        // MetaQuery.list() 对 datetime 列返回 LocalDateTime，须经 toDate 转换（直接 instanceof Date 会静默丢值）
        Date createdAt = MailMessageService.toDate(createAt);
        map.put("createdAt", createdAt == null ? "" : createdAt.toInstant().toString());
        return map;
    }

    private MailFolderCustom toEntity(Map<String, Object> row) {
        MailFolderCustom folder = new MailFolderCustom();
        folder.setId(str(row.get("id")));
        folder.setAccountId(str(row.get("accountId")));
        folder.setUserId(str(row.get("userId")));
        folder.setName(str(row.get("name")));
        folder.setParentId(str(row.get("parentId")));
        Object sortOrder = row.get("sortOrder");
        folder.setSortOrder(sortOrder instanceof Number n ? n.intValue() : 0);
        folder.setTenantCode(str(row.get("tenantCode")));
        Object delStatus = row.get("delStatus");
        folder.setDelStatus(delStatus instanceof Number n ? n.intValue() : 0);
        folder.setCreateAt(MailMessageService.toDate(row.get("createAt")));
        folder.setUpdateAt(MailMessageService.toDate(row.get("updateAt")));
        folder.setCreator(str(row.get("creator")));
        folder.setCreatorName(str(row.get("creatorName")));
        folder.setUpdater(str(row.get("updater")));
        folder.setUpdaterName(str(row.get("updaterName")));
        return folder;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** 自定义文件夹请求（与前端 createCustomFolder/updateCustomFolder 对齐） */
    @lombok.Data
    public static class FolderRequest {
        private String name;
        private String parentId;
        private Integer sortOrder;
        private String accountId;
    }
}
