package cn.geelato.mail.contact.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.query.Filter;
import cn.geelato.orm.query.MetaQuery;
import cn.geelato.orm.query.Order;
import cn.geelato.mail.contact.entity.MailContact;
import cn.geelato.mail.contact.entity.MailContactGroup;
import cn.geelato.mail.service.MailMessageService;
import cn.geelato.mail.util.MailSessionCtx;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 邮件联系人分组服务：CRUD + contactCount 实时聚合 + 删除分组联系人置未分组。
 *
 * 数据隔离：全部按当前登录用户 userId 过滤；修改/删除做归属校验（getOwned）。
 * 分组为用户级（跨账户共享，无 account_id 维度）；前端 queryContactGroups 的
 * accountId 参数对分组不产生过滤效果（联系人列表才按账户过滤）。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@Service
public class MailContactGroupService {

    @Autowired
    @Qualifier("dynamicDao")
    private Dao dynamicDao;

    @Autowired
    private MailContactService contactService;

    // ==================== 查询 ====================

    /** 当前用户分组列表（含各分组联系人计数，实时聚合） */
    public List<Map<String, Object>> list() {
        List<MailContactGroup> groups = listEntities();
        Map<String, Long> counts = countByGroup();
        return groups.stream()
                .map(g -> toResponse(g, counts.getOrDefault(g.getId(), 0L)))
                .collect(Collectors.toList());
    }

    /** 当前用户分组实体列表（按 sortOrder/创建时间排序） */
    public List<MailContactGroup> listEntities() {
        MetaQuery query = MetaFactory.query(MailContactGroup.class)
                .where(Filter.eq("userId", MailSessionCtx.getCurrentUserId()),
                        Filter.eq("delStatus", 0))
                .order(Order.asc("sortOrder"), Order.asc("createAt"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        return rows.stream().map(this::toEntity).collect(Collectors.toList());
    }

    /** 查询并校验归属当前用户（越权/不存在返回 null） */
    public MailContactGroup getOwned(String id) {
        MetaQuery query = MetaFactory.query(MailContactGroup.class)
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

    /** 创建分组（name 必填且用户级去重；sortOrder 取当前用户最大值 + 1） */
    public MailContactGroup create(String name) {
        String trimmed = validateName(name);
        if (findByName(trimmed) != null) {
            throw new IllegalArgumentException("分组已存在: " + trimmed);
        }
        String userId = MailSessionCtx.getCurrentUserId();
        String userName = MailSessionCtx.getCurrentUserName();
        Date now = new Date();
        MailContactGroup group = new MailContactGroup();
        group.setUserId(userId);
        group.setName(trimmed);
        group.setSortOrder(nextSortOrder());
        group.setTenantCode(MailSessionCtx.getCurrentTenantCode());
        group.setDelStatus(0);
        group.setCreateAt(now);
        group.setUpdateAt(now);
        group.setCreator(userId);
        group.setCreatorName(userName);
        group.setUpdater(userId);
        group.setUpdaterName(userName);
        Map<String, Object> saved = dynamicDao.save(group);
        if (group.getId() == null && saved != null && saved.get("id") != null) {
            group.setId(String.valueOf(saved.get("id")));
        }
        return group;
    }

    /** 局部更新分组（name/sortOrder，仅更新出现字段；重名校验排除自身） */
    public void update(MailContactGroup group, String name, Integer sortOrder) {
        if (name != null && !name.isBlank()) {
            String trimmed = validateName(name);
            MailContactGroup existing = findByName(trimmed);
            if (existing != null && !existing.getId().equals(group.getId())) {
                throw new IllegalArgumentException("分组已存在: " + trimmed);
            }
            group.setName(trimmed);
        }
        if (sortOrder != null) {
            group.setSortOrder(sortOrder);
        }
        group.setUpdateAt(new Date());
        group.setUpdater(MailSessionCtx.getCurrentUserId());
        group.setUpdaterName(MailSessionCtx.getCurrentUserName());
        dynamicDao.save(group);
    }

    /**
     * 逻辑删除分组，并将分组下联系人的 group_id 置 NULL（未分组）。
     * 与 mock 契约一致（删除分组后联系人归入"未分组"，不级联删除联系人）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(MailContactGroup group) {
        contactService.ungroupContacts(group.getId());

        group.setDelStatus(1);
        group.setDeleteAt(new Date());
        group.setUpdateAt(new Date());
        group.setUpdater(MailSessionCtx.getCurrentUserId());
        group.setUpdaterName(MailSessionCtx.getCurrentUserName());
        dynamicDao.save(group);
    }

    // ==================== 聚合 ====================

    /** 各分组联系人计数（一次扫描当前用户联系人，Java 侧计数） */
    private Map<String, Long> countByGroup() {
        MetaQuery query = MetaFactory.query(MailContact.class)
                .where(Filter.eq("userId", MailSessionCtx.getCurrentUserId()),
                        Filter.eq("delStatus", 0));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        Map<String, Long> counts = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object groupId = row.get("groupId");
            if (groupId != null && !String.valueOf(groupId).isBlank()) {
                counts.merge(String.valueOf(groupId), 1L, Long::sum);
            }
        }
        return counts;
    }

    // ==================== 响应转换 ====================

    /** 转前端 MailContactGroup 契约（id 为雪花 string，与 P0/P1 id 口径一致） */
    public Map<String, Object> toResponse(MailContactGroup group, long contactCount) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", group.getId());
        map.put("name", group.getName());
        map.put("contactCount", (int) contactCount);
        map.put("sortOrder", group.getSortOrder());
        return map;
    }

    // ==================== 内部辅助 ====================

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("分组名称不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 64) {
            throw new IllegalArgumentException("分组名称超长（上限 64 字符）: " + trimmed.length());
        }
        return trimmed;
    }

    /** 按名称（精确匹配，大小写不敏感）定位当前用户未删除分组（去重判定用；无则 null） */
    private MailContactGroup findByName(String name) {
        String target = name.toLowerCase();
        for (MailContactGroup g : listEntities()) {
            if (g.getName() != null && g.getName().toLowerCase().equals(target)) {
                return g;
            }
        }
        return null;
    }

    private int nextSortOrder() {
        int max = 0;
        for (MailContactGroup g : listEntities()) {
            max = Math.max(max, g.getSortOrder());
        }
        return max + 1;
    }

    /** 查询行转实体（MetaQuery 返回 Map，字段名 camelCase） */
    private MailContactGroup toEntity(Map<String, Object> row) {
        MailContactGroup group = new MailContactGroup();
        group.setId(str(row.get("id")));
        group.setUserId(str(row.get("userId")));
        group.setName(str(row.get("name")));
        Object sortOrder = row.get("sortOrder");
        group.setSortOrder(sortOrder instanceof Number n ? n.intValue() : 0);
        group.setTenantCode(str(row.get("tenantCode")));
        Object delStatus = row.get("delStatus");
        group.setDelStatus(delStatus instanceof Number n ? n.intValue() : 0);
        group.setCreateAt(MailMessageService.toDate(row.get("createAt")));
        group.setUpdateAt(MailMessageService.toDate(row.get("updateAt")));
        group.setCreator(str(row.get("creator")));
        group.setCreatorName(str(row.get("creatorName")));
        group.setUpdater(str(row.get("updater")));
        group.setUpdaterName(str(row.get("updaterName")));
        return group;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
