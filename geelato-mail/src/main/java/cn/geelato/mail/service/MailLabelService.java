package cn.geelato.mail.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.query.Filter;
import cn.geelato.orm.query.MetaQuery;
import cn.geelato.orm.query.Order;
import cn.geelato.mail.entity.MailLabel;
import cn.geelato.mail.entity.MailMessage;
import cn.geelato.mail.util.MailSessionCtx;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 邮件标签服务：CRUD + 归属校验 + 未读数聚合 + 邮件-标签关联维护。
 *
 * 数据隔离：全部按当前登录用户 userId 过滤；修改/删除做归属校验。
 *
 * 账户维度：accountId 为空表示用户级共享标签（跨账户可见）；查询带 accountId 时
 * 返回「该账户标签 + 共享标签」。Fluent DSL 扁平 FilterGroup 不支持 (a=? OR a IS NULL)
 * 与 userId 的混合嵌套，账户过滤在 Java 侧完成（用户标签量级为数十条，无性能问题）。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@Service
public class MailLabelService {

    @Autowired
    @Qualifier("dynamicDao")
    private Dao dynamicDao;

    // ==================== 查询 ====================

    /**
     * 当前用户标签列表（含未读数聚合）。
     *
     * @param accountId 可空；给定返回「该账户标签 + 用户级共享标签」，空返回全部
     */
    public List<Map<String, Object>> list(String accountId) {
        List<MailLabel> labels = listEntities(accountId);
        Map<String, Long> unreadCounts = countUnreadByLabel();
        return labels.stream()
                .map(l -> toResponse(l, unreadCounts.getOrDefault(l.getId(), 0L)))
                .collect(Collectors.toList());
    }

    /** 当前用户标签实体列表（按 sortOrder/创建时间排序） */
    public List<MailLabel> listEntities(String accountId) {
        MetaQuery query = MetaFactory.query(MailLabel.class)
                .where(Filter.eq("userId", MailSessionCtx.getCurrentUserId()),
                        Filter.eq("delStatus", 0))
                .order(Order.asc("sortOrder"), Order.asc("createAt"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        return rows.stream().map(this::toEntity)
                .filter(l -> accountId == null || accountId.isBlank()
                        || l.getAccountId() == null || accountId.equals(l.getAccountId()))
                .collect(Collectors.toList());
    }

    /** 查询并校验归属当前用户（越权/不存在返回 null） */
    public MailLabel getOwned(String id) {
        MetaQuery query = MetaFactory.query(MailLabel.class)
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

    /** 按 id 集合批量定位当前用户标签（setLabels 归属校验/列表标签解析用） */
    public Map<String, MailLabel> mapByIds(Collection<String> ids) {
        Map<String, MailLabel> result = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        MetaQuery query = MetaFactory.query(MailLabel.class)
                .where(Filter.in("id", ids.toArray()),
                        Filter.eq("userId", MailSessionCtx.getCurrentUserId()),
                        Filter.eq("delStatus", 0));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        for (Map<String, Object> row : rows) {
            MailLabel label = toEntity(row);
            result.put(label.getId(), label);
        }
        return result;
    }

    // ==================== 写 ====================

    /** 创建标签（sortOrder 取当前用户最大值 + 1） */
    public MailLabel create(String name, String color, String accountId) {
        String userId = MailSessionCtx.getCurrentUserId();
        String userName = MailSessionCtx.getCurrentUserName();
        Date now = new Date();
        MailLabel label = new MailLabel();
        label.setUserId(userId);
        label.setAccountId(accountId == null || accountId.isBlank() ? null : accountId);
        label.setName(name.trim());
        label.setColor(color == null || color.isBlank() ? "#165dff" : color.trim());
        label.setSortOrder(nextSortOrder(userId));
        label.setTenantCode(MailSessionCtx.getCurrentTenantCode());
        label.setDelStatus(0);
        label.setCreateAt(now);
        label.setUpdateAt(now);
        label.setCreator(userId);
        label.setCreatorName(userName);
        label.setUpdater(userId);
        label.setUpdaterName(userName);
        Map<String, Object> saved = dynamicDao.save(label);
        if (label.getId() == null && saved != null && saved.get("id") != null) {
            label.setId(String.valueOf(saved.get("id")));
        }
        return label;
    }

    /** 局部更新标签（name/color/sortOrder，仅更新出现字段） */
    public void update(MailLabel label, String name, String color, Integer sortOrder) {
        if (name != null && !name.isBlank()) {
            label.setName(name.trim());
        }
        if (color != null && !color.isBlank()) {
            label.setColor(color.trim());
        }
        if (sortOrder != null) {
            label.setSortOrder(sortOrder);
        }
        label.setUpdateAt(new Date());
        label.setUpdater(MailSessionCtx.getCurrentUserId());
        label.setUpdaterName(MailSessionCtx.getCurrentUserName());
        dynamicDao.save(label);
    }

    /**
     * 逻辑删除标签，并从当前用户全部邮件的 label_ids 中摘除该标签。
     * 不摘除会遗留悬空引用（列表标签解析时静默丢弃也可展示，但数据不洁）。
     */
    public void delete(MailLabel label) {
        String userId = MailSessionCtx.getCurrentUserId();
        String userName = MailSessionCtx.getCurrentUserName();
        Date now = new Date();

        stripLabelFromMessages(label.getId(), userId, userName, now);

        label.setDelStatus(1);
        label.setDeleteAt(now);
        label.setUpdateAt(now);
        label.setUpdater(userId);
        label.setUpdaterName(userName);
        dynamicDao.save(label);
    }

    /** 从当前用户邮件的 label_ids 中摘除指定标签（与 MailFolderController 删除迁邮件同模式） */
    private void stripLabelFromMessages(String labelId, String userId, String userName, Date now) {
        MetaQuery query = MetaFactory.query(MailMessage.class)
                .where(Filter.eq("userId", userId),
                        Filter.eq("delStatus", 0),
                        Filter.like("labelIds", "%\"" + labelId + "\"%"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        for (Map<String, Object> row : rows) {
            Object mailId = row.get("id");
            if (mailId == null) {
                continue;
            }
            MailMessage msg = dynamicDao.queryForObject(MailMessage.class, String.valueOf(mailId));
            if (msg == null) {
                continue;
            }
            List<String> ids = MailMessageService.parseIdArray(msg.getLabelIds());
            if (ids.remove(labelId)) {
                msg.setLabelIds(MailMessageService.writeIdArray(ids));
                msg.setUpdateAt(now);
                msg.setUpdater(userId);
                msg.setUpdaterName(userName);
                dynamicDao.save(msg);
            }
        }
    }

    // ==================== 未读数聚合 ====================

    /** 各标签未读邮件数（一次扫描当前用户邮件 label_ids，Java 侧计数） */
    private Map<String, Long> countUnreadByLabel() {
        var nativeSql = MetaFactory.sql(
                "SELECT label_ids FROM mail_message "
                        + "WHERE user_id = ? AND del_status = 0 AND read_status = 'unread' "
                        + "AND label_ids IS NOT NULL AND label_ids != '[]'");
        nativeSql.param(MailSessionCtx.getCurrentUserId());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = nativeSql.list();
        Map<String, Long> counts = new HashMap<>();
        for (Map<String, Object> row : rows) {
            for (String id : MailMessageService.parseIdArray(str(row.get("labelIds")))) {
                counts.merge(id, 1L, Long::sum);
            }
        }
        return counts;
    }

    // ==================== 响应转换 ====================

    /** 转前端 MailLabel 契约（id 为雪花 string，与 P0/P4 id 口径一致） */
    public Map<String, Object> toResponse(MailLabel label, long unreadCount) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", label.getId());
        map.put("name", label.getName());
        map.put("color", label.getColor());
        map.put("sortOrder", label.getSortOrder());
        // int 输出：JacksonConfig 全局 Long→String 序列化会把 long 输出为 "5" 字符串，
        // 前端 MailLabelSchema.unreadCount 为 z.number()，计数用 int 精度足够且契约对齐
        // （与 MailNotificationController.unreadCount / MailContactGroupService.contactCount 同范式）
        map.put("unreadCount", (int) unreadCount);
        return map;
    }

    /** 转 MailItem.labels 内嵌契约（列表/详情展示用，不含 unreadCount） */
    public Map<String, Object> toEmbeddedResponse(MailLabel label) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", label.getId());
        map.put("name", label.getName());
        map.put("color", label.getColor());
        map.put("sortOrder", label.getSortOrder());
        return map;
    }

    // ==================== 内部辅助 ====================

    private int nextSortOrder(String userId) {
        MetaQuery query = MetaFactory.query(MailLabel.class)
                .where(Filter.eq("userId", userId), Filter.eq("delStatus", 0));
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

    /** 查询行转实体（MetaQuery 返回 Map，字段名 camelCase） */
    private MailLabel toEntity(Map<String, Object> row) {
        MailLabel label = new MailLabel();
        label.setId(str(row.get("id")));
        label.setUserId(str(row.get("userId")));
        label.setAccountId(str(row.get("accountId")));
        label.setName(str(row.get("name")));
        label.setColor(str(row.get("color")));
        Object sortOrder = row.get("sortOrder");
        label.setSortOrder(sortOrder instanceof Number n ? n.intValue() : 0);
        label.setTenantCode(str(row.get("tenantCode")));
        Object delStatus = row.get("delStatus");
        label.setDelStatus(delStatus instanceof Number n ? n.intValue() : 0);
        // MetaQuery.list() 对 datetime 列返回 LocalDateTime，须经 toDate 转换（直接 instanceof Date 会静默丢值）
        label.setCreateAt(MailMessageService.toDate(row.get("createAt")));
        label.setUpdateAt(MailMessageService.toDate(row.get("updateAt")));
        label.setCreator(str(row.get("creator")));
        label.setCreatorName(str(row.get("creatorName")));
        label.setUpdater(str(row.get("updater")));
        label.setUpdaterName(str(row.get("updaterName")));
        return label;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
