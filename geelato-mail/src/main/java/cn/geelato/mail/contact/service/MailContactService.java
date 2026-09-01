package cn.geelato.mail.contact.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.query.Filter;
import cn.geelato.orm.query.MetaQuery;
import cn.geelato.orm.query.Order;
import cn.geelato.mail.contact.entity.MailContact;
import cn.geelato.mail.service.MailMessageService;
import cn.geelato.mail.util.MailSessionCtx;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 邮件联系人服务：CRUD + 分页/搜索 + 合并 + 来往历史 + suggest 联想。
 *
 * 数据隔离：全部按当前登录用户 userId 过滤；修改/删除做归属校验（getOwned）。
 *
 * 账户维度：accountId 为空表示用户级共享联系人；查询带 accountId 时返回
 * 「该账户联系人 + 共享联系人」（Java 侧过滤，与 MailLabelService 同模式）。
 *
 * mailCount/lastContactAt：不落列，列表/详情读取时对当前页联系人邮箱做一次
 * mail_message 聚合（原生 SQL 参数绑定防注入；LIKE 通配符转义）。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@Service
public class MailContactService {

    /** 历史/联想查询的邮件扫描上限（防超长拖慢，超出按最新截断） */
    private static final int HISTORY_LIMIT = 50;
    /** suggest 返回条数上限 */
    static final int SUGGEST_MAX_LIMIT = 50;
    static final int SUGGEST_DEFAULT_LIMIT = 10;

    static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // 字段长度上限（对齐 V78 列宽；超长写前置拦截，防落库报 500 "Data too long"）
    static final int MAX_NAME_LEN = 128;
    static final int MAX_EMAIL_LEN = 128;
    static final int MAX_PHONE_LEN = 64;
    static final int MAX_ORG_LEN = 128;
    static final int MAX_AVATAR_LEN = 512;
    static final int MAX_NOTES_LEN = 1024;

    @Autowired
    @Qualifier("dynamicDao")
    private Dao dynamicDao;

    @Autowired
    private MailContactRecentService recentService;

    // ==================== 查询 ====================

    /**
     * 分页查询当前用户联系人（服务端分页）。
     *
     * @param keyword   可空；匹配 name/email/phone 包含（不区分大小写）
     * @param groupId   可空；给定按分组过滤，"0" 表示未分组（group_id IS NULL）
     * @param accountId 可空；给定返回「该账户联系人 + 用户级共享联系人」
     * @return 前端 MailListPage 契约：{list,total,page,pageSize,hasMore}
     */
    public Map<String, Object> list(String keyword, String groupId, String accountId, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        List<MailContact> all = listEntities(accountId);
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        List<MailContact> filtered = all.stream()
                .filter(c -> matchGroup(c, groupId))
                .filter(c -> kw.isEmpty()
                        || (c.getName() != null && c.getName().toLowerCase().contains(kw))
                        || (c.getEmail() != null && c.getEmail().toLowerCase().contains(kw))
                        || (c.getPhone() != null && c.getPhone().toLowerCase().contains(kw)))
                .collect(Collectors.toList());
        int total = filtered.size();
        int from = Math.min((safePage - 1) * safePageSize, total);
        int to = Math.min(from + safePageSize, total);
        List<MailContact> pageItems = filtered.subList(from, to);

        Map<String, long[]> stats = aggregateStats(
                pageItems.stream().map(MailContact::getEmail).collect(Collectors.toList()));
        List<Map<String, Object>> items = pageItems.stream()
                .map(c -> toResponse(c, stats.get(normalizeEmail(c.getEmail()))))
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", items);
        result.put("total", total);
        result.put("page", safePage);
        result.put("pageSize", safePageSize);
        result.put("hasMore", (long) safePage * safePageSize < total);
        return result;
    }

    /** 当前用户联系人实体列表（按创建时间升序，与 mock 数组顺序口径一致） */
    public List<MailContact> listEntities(String accountId) {
        MetaQuery query = MetaFactory.query(MailContact.class)
                .where(Filter.eq("userId", MailSessionCtx.getCurrentUserId()),
                        Filter.eq("delStatus", 0))
                .order(Order.asc("createAt"), Order.asc("id"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        return rows.stream().map(this::toEntity)
                .filter(c -> accountId == null || accountId.isBlank()
                        || c.getAccountId() == null || accountId.equals(c.getAccountId()))
                .collect(Collectors.toList());
    }

    /** 查询并校验归属当前用户（越权/不存在返回 null） */
    public MailContact getOwned(String id) {
        MetaQuery query = MetaFactory.query(MailContact.class)
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

    /** 按邮箱（忽略大小写）定位当前用户未删除联系人（去重/幂等判定用；无则 null） */
    public MailContact findByEmail(String email) {
        String target = normalizeEmail(email);
        if (target == null) {
            return null;
        }
        for (MailContact c : listEntities(null)) {
            if (target.equals(normalizeEmail(c.getEmail()))) {
                return c;
            }
        }
        return null;
    }

    /** 详情（含 mailCount/lastContactAt 聚合） */
    public Map<String, Object> detail(MailContact contact) {
        Map<String, long[]> stats = aggregateStats(List.of(contact.getEmail()));
        return toResponse(contact, stats.get(normalizeEmail(contact.getEmail())));
    }

    // ==================== 写 ====================

    /** 创建联系人（邮箱去重 fail-fast；groupId 归属由 Controller 校验后传入） */
    public MailContact create(String name, String email, String phone, String org,
                              String avatar, String notes, String groupId, String accountId) {
        String normalizedEmail = normalizeEmail(email);
        validateEmail(normalizedEmail);
        if (findByEmail(normalizedEmail) != null) {
            throw new IllegalArgumentException("邮箱已存在: " + normalizedEmail);
        }
        validateFieldLengths(name.trim(), normalizedEmail, blankToNull(phone), blankToNull(org),
                blankToNull(avatar), blankToNull(notes));
        String userId = MailSessionCtx.getCurrentUserId();
        String userName = MailSessionCtx.getCurrentUserName();
        Date now = new Date();
        MailContact contact = new MailContact();
        contact.setUserId(userId);
        contact.setAccountId(blankToNull(accountId));
        contact.setGroupId(blankToNull(groupId));
        contact.setName(name.trim());
        contact.setEmail(normalizedEmail);
        contact.setPhone(blankToNull(phone));
        contact.setOrg(blankToNull(org));
        contact.setAvatar(blankToNull(avatar));
        contact.setNotes(blankToNull(notes));
        contact.setTenantCode(MailSessionCtx.getCurrentTenantCode());
        contact.setDelStatus(0);
        contact.setCreateAt(now);
        contact.setUpdateAt(now);
        contact.setCreator(userId);
        contact.setCreatorName(userName);
        contact.setUpdater(userId);
        contact.setUpdaterName(userName);
        Map<String, Object> saved = dynamicDao.save(contact);
        if (contact.getId() == null && saved != null && saved.get("id") != null) {
            contact.setId(String.valueOf(saved.get("id")));
        }
        return contact;
    }

    /**
     * 局部更新联系人（仅更新出现字段；空串视为清除该字段存 NULL）。
     * 邮箱变更时重新校验格式 + 去重（排除自身）。
     */
    public void update(MailContact contact, String name, String email, String phone, String org,
                       String avatar, String notes, String groupId) {
        if (name != null && !name.isBlank()) {
            contact.setName(name.trim());
        }
        if (email != null && !email.isBlank()) {
            String normalizedEmail = normalizeEmail(email);
            validateEmail(normalizedEmail);
            MailContact existing = findByEmail(normalizedEmail);
            if (existing != null && !existing.getId().equals(contact.getId())) {
                throw new IllegalArgumentException("邮箱已存在: " + normalizedEmail);
            }
            contact.setEmail(normalizedEmail);
        }
        if (phone != null) {
            contact.setPhone(blankToNull(phone));
        }
        if (org != null) {
            contact.setOrg(blankToNull(org));
        }
        if (avatar != null) {
            contact.setAvatar(blankToNull(avatar));
        }
        if (notes != null) {
            contact.setNotes(blankToNull(notes));
        }
        if (groupId != null) {
            contact.setGroupId(blankToNull(groupId));
        }
        validateFieldLengths(contact.getName(), contact.getEmail(), contact.getPhone(),
                contact.getOrg(), contact.getAvatar(), contact.getNotes());
        touch(contact);
        dynamicDao.save(contact);
    }

    /** 逻辑删除联系人 */
    public void delete(MailContact contact) {
        markDeleted(contact);
        dynamicDao.save(contact);
    }

    /**
     * 将当前用户某分组下全部联系人置为未分组（group_id=NULL）。
     * 供分组删除时调用（与 mock 契约一致：删除分组不级联删联系人）。
     *
     * @return 实际置未分组的联系人数
     */
    public int ungroupContacts(String groupId) {
        String userId = MailSessionCtx.getCurrentUserId();
        MetaQuery query = MetaFactory.query(MailContact.class)
                .where(Filter.eq("userId", userId),
                        Filter.eq("groupId", groupId),
                        Filter.eq("delStatus", 0));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        int affected = 0;
        for (Map<String, Object> row : rows) {
            Object contactId = row.get("id");
            if (contactId == null) {
                continue;
            }
            MailContact contact = dynamicDao.queryForObject(MailContact.class, String.valueOf(contactId));
            if (contact == null) {
                continue;
            }
            contact.setGroupId(null);
            touch(contact);
            dynamicDao.save(contact);
            affected++;
        }
        return affected;
    }

    /** 批量逻辑删除（仅删除归属当前用户的；返回实际删除条数） */
    @Transactional(rollbackFor = Exception.class)
    public int batchDelete(List<String> ids) {
        int affected = 0;
        for (String id : ids) {
            MailContact contact = getOwned(id);
            if (contact == null) {
                continue;
            }
            markDeleted(contact);
            dynamicDao.save(contact);
            affected++;
        }
        return affected;
    }

    /**
     * 合并联系人：第二联系人非空白字段回填主联系人的空白字段（phone/org/avatar/notes/groupId），
     * 随后逻辑删除第二联系人。name/email 永不覆盖（主联系人标识不可变）。
     *
     * 任一次要联系人不存在/越权则不产生任何写入（事务性，与 mock 契约一致）。
     *
     * @return 未命中的次要联系人 id 列表（空 = 全部合并成功）
     */
    @Transactional(rollbackFor = Exception.class)
    public List<String> merge(MailContact primary, List<String> secondaryIds) {
        List<MailContact> secondaries = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();
        for (String sid : secondaryIds) {
            // 不变量保护：次要联系人含主联系人自身时 fail-fast（否则主联系人会被逻辑删除自毁）
            if (primary.getId() != null && primary.getId().equals(sid)) {
                throw new IllegalArgumentException("次要联系人不能包含主联系人自身: " + sid);
            }
            MailContact secondary = getOwned(sid);
            if (secondary == null) {
                failedIds.add(sid);
            } else {
                secondaries.add(secondary);
            }
        }
        if (!failedIds.isEmpty()) {
            return failedIds;
        }
        if (applyMerge(primary, secondaries)) {
            touch(primary);
            dynamicDao.save(primary);
        }
        for (MailContact secondary : secondaries) {
            markDeleted(secondary);
            dynamicDao.save(secondary);
        }
        return List.of();
    }

    /**
     * 合并字段回填（纯函数，单测用）：主联系人空白字段从次要联系人按序取首个非空白值。
     *
     * @return 是否有字段被回填
     */
    static boolean applyMerge(MailContact primary, List<MailContact> secondaries) {
        boolean changed = false;
        for (MailContact s : secondaries) {
            if (isBlank(primary.getPhone()) && !isBlank(s.getPhone())) {
                primary.setPhone(s.getPhone());
                changed = true;
            }
            if (isBlank(primary.getOrg()) && !isBlank(s.getOrg())) {
                primary.setOrg(s.getOrg());
                changed = true;
            }
            if (isBlank(primary.getAvatar()) && !isBlank(s.getAvatar())) {
                primary.setAvatar(s.getAvatar());
                changed = true;
            }
            if (isBlank(primary.getNotes()) && !isBlank(s.getNotes())) {
                primary.setNotes(s.getNotes());
                changed = true;
            }
            if (isBlank(primary.getGroupId()) && !isBlank(s.getGroupId())) {
                primary.setGroupId(s.getGroupId());
                changed = true;
            }
        }
        return changed;
    }

    // ==================== 来往历史 ====================

    /**
     * 联系人来往邮件历史（实时由 mail_message 推导，不建表）。
     * direction：from_email 命中 → inbound；to/cc/bcc 命中 → outbound。
     * 按 send_date 倒序，上限 {@value #HISTORY_LIMIT} 条。
     */
    public List<Map<String, Object>> history(MailContact contact) {
        String email = normalizeEmail(contact.getEmail());
        var nativeSql = MetaFactory.sql(
                // 原生 SQL 结果键 = getColumnLabel 原样返回（无驼峰转换），下划线列必须 AS 驼峰别名
                "SELECT id, subject, send_date AS sendDate, from_email AS fromEmail FROM mail_message "
                        + "WHERE user_id = ? AND del_status = 0 AND is_draft = 0 "
                        + "AND (from_email = ? OR to_json LIKE ? OR cc_json LIKE ? OR bcc_json LIKE ?) "
                        + "ORDER BY send_date DESC LIMIT " + HISTORY_LIMIT);
        String like = "%\"" + escapeLike(email) + "\"%";
        nativeSql.param(MailSessionCtx.getCurrentUserId());
        nativeSql.param(email);
        nativeSql.param(like);
        nativeSql.param(like);
        nativeSql.param(like);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = nativeSql.list();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", str(row.get("id")));
            item.put("subject", str(row.get("subject")) == null ? "" : str(row.get("subject")));
            Date sendDate = MailMessageService.toDate(row.get("sendDate"));
            item.put("sentAt", sendDate == null ? "" : sendDate.toInstant().toString());
            String fromEmail = str(row.get("fromEmail"));
            item.put("direction", email.equalsIgnoreCase(fromEmail == null ? "" : fromEmail)
                    ? "inbound" : "outbound");
            result.add(item);
        }
        return result;
    }

    // ==================== suggest 联想 ====================

    /**
     * 收件人联想（契约缺口补建：compose TODO P1-F5）。
     * 数据源：联系人（name/email 前缀匹配，创建时间升序）+ 最近收件人（同口径，按最近使用倒序）。
     * 按 lower(email) 去重，联系人优先。q 为空时仅返回最近收件人（撰写页初始下拉）。
     *
     * @return [{id,name,email,source}]；id 对 recent-only 条目为 null
     */
    public List<Map<String, Object>> suggest(String q, int limit) {
        int safeLimit = limit <= 0 ? SUGGEST_DEFAULT_LIMIT : Math.min(limit, SUGGEST_MAX_LIMIT);
        String prefix = q == null ? "" : q.trim().toLowerCase();
        List<Map<String, Object>> contactItems = new ArrayList<>();
        if (!prefix.isEmpty()) {
            for (MailContact c : listEntities(null)) {
                if (prefixMatch(c.getName(), prefix) || prefixMatch(c.getEmail(), prefix)) {
                    contactItems.add(suggestItem(c.getId(), displayName(c), c.getEmail(), "contact"));
                }
            }
        }
        List<Map<String, Object>> recentItems = recentService.list(prefix, safeLimit).stream()
                .map(r -> suggestItem(null,
                        r.getName() == null ? r.getEmail() : r.getName(), r.getEmail(), "recent"))
                .collect(Collectors.toList());
        return mergeSuggest(contactItems, recentItems, safeLimit);
    }

    /**
     * suggest 合并（纯函数，单测用）：联系人优先，recent 补齐；按 lower(email) 去重；截断到 limit。
     */
    static List<Map<String, Object>> mergeSuggest(List<Map<String, Object>> contactItems,
                                                  List<Map<String, Object>> recentItems, int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> item : contactItems) {
            if (result.size() >= limit) {
                break;
            }
            if (seen.add(normalizeEmail(String.valueOf(item.get("email"))))) {
                result.add(item);
            }
        }
        for (Map<String, Object> item : recentItems) {
            if (result.size() >= limit) {
                break;
            }
            if (seen.add(normalizeEmail(String.valueOf(item.get("email"))))) {
                result.add(item);
            }
        }
        return result;
    }

    // ==================== 邮件聚合统计 ====================

    /**
     * 当前页联系人邮箱的 mailCount/lastContactAt 聚合（一次 SQL，Java 侧计数）。
     * key = lower(email)；value = [mailCount, lastContactAtEpochMilli]（无往来则不出现）。
     *
     * 实现：mail_message 按 user_id + del_status + is_draft 过滤后，以
     * from_email IN (...) OR to/cc/bcc_json LIKE 预筛行，再 Java 解析 JSON 精确匹配。
     * LIKE 值经 {@link #escapeLike} 转义 %/_。
     */
    private Map<String, long[]> aggregateStats(Collection<String> emails) {
        Map<String, long[]> stats = new HashMap<>();
        if (emails == null || emails.isEmpty()) {
            return stats;
        }
        Set<String> targets = emails.stream()
                .map(MailContactService::normalizeEmail)
                .filter(e -> e != null && !e.isEmpty())
                .collect(Collectors.toSet());
        if (targets.isEmpty()) {
            return stats;
        }
        StringBuilder sql = new StringBuilder(
                // 原生 SQL 结果键 = getColumnLabel 原样返回（无驼峰转换），下划线列必须 AS 驼峰别名
                "SELECT from_email AS fromEmail, to_json AS toJson, cc_json AS ccJson, "
                        + "bcc_json AS bccJson, send_date AS sendDate FROM mail_message "
                        + "WHERE user_id = ? AND del_status = 0 AND is_draft = 0 AND (");
        List<Object> params = new ArrayList<>();
        params.add(MailSessionCtx.getCurrentUserId());
        sql.append("from_email IN (").append(placeholders(targets.size())).append(")");
        params.addAll(targets);
        for (String email : targets) {
            String like = "%\"" + escapeLike(email) + "\"%";
            sql.append(" OR to_json LIKE ? OR cc_json LIKE ? OR bcc_json LIKE ?");
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append(")");
        var nativeSql = MetaFactory.sql(sql.toString());
        for (Object p : params) {
            nativeSql.param(p);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = nativeSql.list();
        for (Map<String, Object> row : rows) {
            Date sendDate = MailMessageService.toDate(row.get("sendDate"));
            long epoch = sendDate == null ? 0L : sendDate.getTime();
            Set<String> hitEmails = new HashSet<>();
            String fromEmail = normalizeEmail(str(row.get("fromEmail")));
            if (fromEmail != null && targets.contains(fromEmail)) {
                hitEmails.add(fromEmail);
            }
            hitEmails.addAll(matchJsonEmails(str(row.get("toJson")), targets));
            hitEmails.addAll(matchJsonEmails(str(row.get("ccJson")), targets));
            hitEmails.addAll(matchJsonEmails(str(row.get("bccJson")), targets));
            for (String hit : hitEmails) {
                long[] stat = stats.computeIfAbsent(hit, k -> new long[]{0L, 0L});
                stat[0]++;
                stat[1] = Math.max(stat[1], epoch);
            }
        }
        return stats;
    }

    /** 解析地址 JSON 数组 [{"name","email"}]，返回命中 targets 的 lower(email) 集合 */
    private Set<String> matchJsonEmails(String json, Set<String> targets) {
        Set<String> hits = new HashSet<>();
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return hits;
        }
        try {
            List<Map<String, Object>> addresses = MAPPER.readValue(json,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, Map.class));
            for (Map<String, Object> addr : addresses) {
                String email = normalizeEmail(str(addr.get("email")));
                if (email != null && targets.contains(email)) {
                    hits.add(email);
                }
            }
        } catch (Exception e) {
            // 与 attachmentMetadata 同口径 fail-fast：to_json 由本系统 Jackson 写入，损坏即数据异常，禁止静默吞掉
            throw new IllegalStateException("邮件地址 JSON 解析失败: " + json, e);
        }
        return hits;
    }

    // ==================== 响应转换 ====================

    /** 转前端 MailContact 契约（id 为雪花 string，与 P0/P1 迁移口径一致） */
    public Map<String, Object> toResponse(MailContact contact, long[] stat) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", contact.getId());
        map.put("name", contact.getName());
        map.put("email", contact.getEmail());
        if (contact.getPhone() != null) {
            map.put("phone", contact.getPhone());
        }
        if (contact.getOrg() != null) {
            map.put("org", contact.getOrg());
        }
        if (contact.getAvatar() != null) {
            map.put("avatar", contact.getAvatar());
        }
        if (contact.getGroupId() != null) {
            map.put("groupId", contact.getGroupId());
        }
        if (contact.getNotes() != null) {
            map.put("notes", contact.getNotes());
        }
        if (stat != null && stat[0] > 0) {
            map.put("mailCount", (int) stat[0]);
            map.put("lastContactAt", new Date(stat[1]).toInstant().toString());
        }
        map.put("createdAt", contact.getCreateAt() == null
                ? "" : contact.getCreateAt().toInstant().toString());
        return map;
    }

    // ==================== 内部辅助 ====================

    /** 邮箱规范化（去空白 + 小写；去重/匹配同口径） */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    static void validateEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("邮箱格式非法: " + email);
        }
    }

    /** 字段长度 fail-fast 校验（上限对齐 V78 列宽；传入值须为最终落库值） */
    static void validateFieldLengths(String name, String email, String phone,
                                     String org, String avatar, String notes) {
        checkLength("姓名", name, MAX_NAME_LEN);
        checkLength("邮箱", email, MAX_EMAIL_LEN);
        checkLength("电话", phone, MAX_PHONE_LEN);
        checkLength("公司/组织", org, MAX_ORG_LEN);
        checkLength("头像URL", avatar, MAX_AVATAR_LEN);
        checkLength("备注", notes, MAX_NOTES_LEN);
    }

    private static void checkLength(String label, String value, int max) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(
                    label + "超长（上限 " + max + " 字符，实际 " + value.length() + "）");
        }
    }

    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean prefixMatch(String value, String lowerPrefix) {
        return value != null && value.toLowerCase().startsWith(lowerPrefix);
    }

    private static String displayName(MailContact c) {
        return c.getName() == null || c.getName().isBlank() ? c.getEmail() : c.getName();
    }

    private static Map<String, Object> suggestItem(String id, String name, String email, String source) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("name", name);
        item.put("email", email);
        item.put("source", source);
        return item;
    }

    private boolean matchGroup(MailContact c, String groupId) {
        if (groupId == null || groupId.isBlank()) {
            return true;
        }
        if ("0".equals(groupId)) {
            return c.getGroupId() == null;
        }
        return groupId.equals(c.getGroupId());
    }

    /** LIKE 通配符转义（%/_ 前缀反斜杠；邮箱 local-part 合法字符含 %，必须转义） */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String placeholders(int n) {
        return String.join(", ", java.util.Collections.nCopies(n, "?"));
    }

    private void touch(MailContact contact) {
        contact.setUpdateAt(new Date());
        contact.setUpdater(MailSessionCtx.getCurrentUserId());
        contact.setUpdaterName(MailSessionCtx.getCurrentUserName());
    }

    private void markDeleted(MailContact contact) {
        contact.setDelStatus(1);
        contact.setDeleteAt(new Date());
        touch(contact);
    }

    /** 查询行转实体（MetaQuery 返回 Map，字段名 camelCase） */
    private MailContact toEntity(Map<String, Object> row) {
        MailContact contact = new MailContact();
        contact.setId(str(row.get("id")));
        contact.setUserId(str(row.get("userId")));
        contact.setAccountId(str(row.get("accountId")));
        contact.setGroupId(str(row.get("groupId")));
        contact.setName(str(row.get("name")));
        contact.setEmail(str(row.get("email")));
        contact.setPhone(str(row.get("phone")));
        contact.setOrg(str(row.get("org")));
        contact.setAvatar(str(row.get("avatar")));
        contact.setNotes(str(row.get("notes")));
        contact.setTenantCode(str(row.get("tenantCode")));
        Object delStatus = row.get("delStatus");
        contact.setDelStatus(delStatus instanceof Number n ? n.intValue() : 0);
        contact.setCreateAt(MailMessageService.toDate(row.get("createAt")));
        contact.setUpdateAt(MailMessageService.toDate(row.get("updateAt")));
        contact.setCreator(str(row.get("creator")));
        contact.setCreatorName(str(row.get("creatorName")));
        contact.setUpdater(str(row.get("updater")));
        contact.setUpdaterName(str(row.get("updaterName")));
        return contact;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
