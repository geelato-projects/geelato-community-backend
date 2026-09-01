package cn.geelato.mail.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.query.Filter;
import cn.geelato.orm.query.MetaQuery;
import cn.geelato.orm.query.Order;
import cn.geelato.mail.entity.MailSignature;
import cn.geelato.mail.util.MailSessionCtx;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 邮件签名服务：CRUD + 归属校验 + 默认签名唯一性维护。
 *
 * 数据隔离：全部按当前登录用户 userId 过滤；修改/删除做归属校验。
 *
 * 账户维度：accountId 为空表示用户级共享签名（跨账户可见）；查询带 accountId 时
 * 返回「该账户签名 + 共享签名」。Fluent DSL 扁平 FilterGroup 不支持 (a=? OR a IS NULL)
 * 与 userId 的混合嵌套，账户过滤在 Java 侧完成（用户签名量级为个位数，无性能问题）。
 *
 * 默认签名唯一性：is_default=1 在同一 userId 下唯一（Service 层保证，设默认时清除其他）。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@Service
public class MailSignatureService {

    @Autowired
    @Qualifier("dynamicDao")
    private Dao dynamicDao;

    // ==================== 查询 ====================

    /** 当前用户签名列表（默认签名在前，其余按创建时间升序） */
    public List<Map<String, Object>> list(String accountId) {
        return listEntities(accountId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** 当前用户签名实体列表 */
    public List<MailSignature> listEntities(String accountId) {
        MetaQuery query = MetaFactory.query(MailSignature.class)
                .where(Filter.eq("userId", MailSessionCtx.getCurrentUserId()),
                        Filter.eq("delStatus", 0))
                .order(Order.desc("isDefault"), Order.asc("createAt"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        return rows.stream().map(this::toEntity)
                .filter(s -> accountId == null || accountId.isBlank()
                        || s.getAccountId() == null || accountId.equals(s.getAccountId()))
                .collect(Collectors.toList());
    }

    /** 查询并校验归属当前用户（越权/不存在返回 null） */
    public MailSignature getOwned(String id) {
        MetaQuery query = MetaFactory.query(MailSignature.class)
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

    // ==================== 写 ====================

    /** 创建签名（当前用户首个签名自动设为默认） */
    public MailSignature create(String name, String content, String accountId) {
        String userId = MailSessionCtx.getCurrentUserId();
        String userName = MailSessionCtx.getCurrentUserName();
        Date now = new Date();
        MailSignature signature = new MailSignature();
        signature.setUserId(userId);
        signature.setAccountId(accountId == null || accountId.isBlank() ? null : accountId);
        signature.setName(name.trim());
        signature.setContent(content);
        signature.setIsDefault(listEntities(null).isEmpty() ? 1 : 0);
        signature.setTenantCode(MailSessionCtx.getCurrentTenantCode());
        signature.setDelStatus(0);
        signature.setCreateAt(now);
        signature.setUpdateAt(now);
        signature.setCreator(userId);
        signature.setCreatorName(userName);
        signature.setUpdater(userId);
        signature.setUpdaterName(userName);
        Map<String, Object> saved = dynamicDao.save(signature);
        if (signature.getId() == null && saved != null && saved.get("id") != null) {
            signature.setId(String.valueOf(saved.get("id")));
        }
        return signature;
    }

    /** 局部更新签名（name/content/isDefault，仅更新出现字段；设默认时清除其他默认） */
    public void update(MailSignature signature, String name, String content, Boolean isDefault) {
        if (name != null && !name.isBlank()) {
            signature.setName(name.trim());
        }
        if (content != null) {
            signature.setContent(content);
        }
        if (Boolean.TRUE.equals(isDefault)) {
            clearDefaultForOthers(signature.getUserId(), signature.getId());
            signature.setIsDefault(1);
        } else if (Boolean.FALSE.equals(isDefault)) {
            signature.setIsDefault(0);
        }
        signature.setUpdateAt(new Date());
        signature.setUpdater(MailSessionCtx.getCurrentUserId());
        signature.setUpdaterName(MailSessionCtx.getCurrentUserName());
        dynamicDao.save(signature);
    }

    /** 逻辑删除签名 */
    public void delete(MailSignature signature) {
        String userId = MailSessionCtx.getCurrentUserId();
        String userName = MailSessionCtx.getCurrentUserName();
        Date now = new Date();
        signature.setDelStatus(1);
        signature.setDeleteAt(now);
        signature.setUpdateAt(now);
        signature.setUpdater(userId);
        signature.setUpdaterName(userName);
        dynamicDao.save(signature);
    }

    // ==================== 响应转换 ====================

    /** 转前端 MailSignature 契约（id 为雪花 string，与 P0/P4 id 口径一致） */
    public Map<String, Object> toResponse(MailSignature signature) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", signature.getId());
        map.put("name", signature.getName());
        map.put("content", signature.getContent() == null ? "" : signature.getContent());
        map.put("isDefault", signature.getIsDefault() == 1);
        return map;
    }

    // ==================== 内部辅助 ====================

    /** 清除当前用户其他签名的默认标记（保证 is_default 每用户唯一） */
    private void clearDefaultForOthers(String userId, String exceptId) {
        MetaQuery query = MetaFactory.query(MailSignature.class)
                .where(Filter.eq("userId", userId),
                        Filter.eq("isDefault", 1),
                        Filter.eq("delStatus", 0));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        Date now = new Date();
        for (Map<String, Object> row : rows) {
            Object rowId = row.get("id");
            if (rowId == null || String.valueOf(rowId).equals(exceptId)) {
                continue;
            }
            MailSignature other = dynamicDao.queryForObject(MailSignature.class, String.valueOf(rowId));
            if (other == null) {
                continue;
            }
            other.setIsDefault(0);
            other.setUpdateAt(now);
            other.setUpdater(userId);
            other.setUpdaterName(MailSessionCtx.getCurrentUserName());
            dynamicDao.save(other);
        }
    }

    /** 查询行转实体（MetaQuery 返回 Map，字段名 camelCase） */
    private MailSignature toEntity(Map<String, Object> row) {
        MailSignature signature = new MailSignature();
        signature.setId(str(row.get("id")));
        signature.setUserId(str(row.get("userId")));
        signature.setAccountId(str(row.get("accountId")));
        signature.setName(str(row.get("name")));
        signature.setContent(str(row.get("content")));
        Object isDefault = row.get("isDefault");
        signature.setIsDefault(isDefault instanceof Number n ? n.intValue() : 0);
        signature.setTenantCode(str(row.get("tenantCode")));
        Object delStatus = row.get("delStatus");
        signature.setDelStatus(delStatus instanceof Number n ? n.intValue() : 0);
        // MetaQuery.list() 对 datetime 列返回 LocalDateTime，须经 toDate 转换（直接 instanceof Date 会静默丢值）
        signature.setCreateAt(MailMessageService.toDate(row.get("createAt")));
        signature.setUpdateAt(MailMessageService.toDate(row.get("updateAt")));
        signature.setCreator(str(row.get("creator")));
        signature.setCreatorName(str(row.get("creatorName")));
        signature.setUpdater(str(row.get("updater")));
        signature.setUpdaterName(str(row.get("updaterName")));
        return signature;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
