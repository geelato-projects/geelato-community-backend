package cn.geelato.mail.contact.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.query.Filter;
import cn.geelato.orm.query.MetaQuery;
import cn.geelato.orm.query.Order;
import cn.geelato.mail.contact.entity.MailContactRecent;
import cn.geelato.mail.service.MailMessageService;
import cn.geelato.mail.util.MailSessionCtx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 最近收件人服务：suggest 数据源之一 + 最近使用记录/查询/清除。
 *
 * 写入入口：发送成功（MailController.send 钩子）按 to/cc/bcc 全量收件人 upsert。
 * 去重键 (user_id, lower(email))：命中则 use_count+1 并刷新 last_used_at/name，
 * 未命中插入新行。每用户上限 {@value #RECENT_CAP} 条，超出按 last_used_at 最旧淘汰。
 *
 * 数据隔离：全部按当前登录用户 userId 过滤。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@Service
public class MailContactRecentService {

    private static final Logger log = LoggerFactory.getLogger(MailContactRecentService.class);

    /** 每用户最近收件人上限（超出按最近使用时间最旧淘汰） */
    static final int RECENT_CAP = 200;

    @Autowired
    @Qualifier("dynamicDao")
    private Dao dynamicDao;

    // ==================== 查询 ====================

    /**
     * suggest 用最近收件人（前缀匹配 name/email，按最近使用倒序）。
     *
     * @param prefix 小写前缀；空串返回全部（撰写页初始下拉）
     */
    public List<MailContactRecent> list(String prefix, int limit) {
        String p = prefix == null ? "" : prefix;
        return listEntities().stream()
                .filter(r -> p.isEmpty()
                        || (r.getName() != null && r.getName().toLowerCase().startsWith(p))
                        || (r.getEmail() != null && r.getEmail().toLowerCase().startsWith(p)))
                .limit(Math.max(1, limit))
                .collect(Collectors.toList());
    }

    /** 最近使用记录查询（GET /contacts/recent），按最近使用倒序 */
    public List<Map<String, Object>> listRecent(int limit) {
        return listEntities().stream()
                .limit(Math.max(1, limit))
                .map(MailContactRecentService::toResponse)
                .collect(Collectors.toList());
    }

    /** 当前用户最近收件人实体列表（按最近使用倒序） */
    private List<MailContactRecent> listEntities() {
        MetaQuery query = MetaFactory.query(MailContactRecent.class)
                .where(Filter.eq("userId", MailSessionCtx.getCurrentUserId()),
                        Filter.eq("delStatus", 0))
                .order(Order.desc("lastUsedAt"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        return rows.stream().map(this::toEntity).collect(Collectors.toList());
    }

    // ==================== 写入 ====================

    /**
     * 发送成功钩子：按 compose 的 to/cc/bcc 全量收件人 upsert 最近记录。
     *
     * <p>FALLBACK:[最近收件人为辅助数据，其写入失败不得掩盖已成功的发送结果]
     * [预期行为: 记录失败仅 warn 日志，发送响应不受影响]
     */
    public void recordCompose(MailMessageService.ComposeRequest compose) {
        try {
            List<MailMessageService.AddressDto> addresses = new ArrayList<>();
            if (compose.getTo() != null) {
                addresses.addAll(compose.getTo());
            }
            if (compose.getCc() != null) {
                addresses.addAll(compose.getCc());
            }
            if (compose.getBcc() != null) {
                addresses.addAll(compose.getBcc());
            }
            recordBatch(addresses);
        } catch (Exception e) {
            log.warn("最近收件人记录失败（发送已成功，不受影响）: {}", e.getMessage());
        }
    }

    /** upsert 一批收件人（非法邮箱跳过；批量内按 lower(email) 去重，先见先得） */
    @Transactional(rollbackFor = Exception.class)
    public void recordBatch(List<MailMessageService.AddressDto> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return;
        }
        String userId = MailSessionCtx.getCurrentUserId();
        List<MailContactRecent> existing = listEntities();
        Date now = new Date();
        Map<String, MailContactRecent> byEmail = existing.stream()
                .collect(Collectors.toMap(r -> MailContactService.normalizeEmail(r.getEmail()), r -> r,
                        (a, b) -> a, LinkedHashMap::new));
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (MailMessageService.AddressDto addr : addresses) {
            String email = MailContactService.normalizeEmail(addr == null ? null : addr.getEmail());
            if (email == null || !MailContactService.EMAIL_PATTERN.matcher(email).matches() || !seen.add(email)) {
                continue;
            }
            String name = MailContactService.blankToNull(addr.getName());
            MailContactRecent hit = byEmail.get(email);
            if (hit != null) {
                hit.setUseCount(hit.getUseCount() + 1);
                hit.setLastUsedAt(now);
                if (name != null) {
                    hit.setName(name);
                }
                touch(hit);
                dynamicDao.save(hit);
            } else {
                MailContactRecent recent = new MailContactRecent();
                recent.setUserId(userId);
                recent.setEmail(email);
                recent.setName(name);
                recent.setUseCount(1);
                recent.setLastUsedAt(now);
                recent.setTenantCode(MailSessionCtx.getCurrentTenantCode());
                recent.setDelStatus(0);
                recent.setCreateAt(now);
                recent.setUpdateAt(now);
                recent.setCreator(userId);
                recent.setCreatorName(MailSessionCtx.getCurrentUserName());
                recent.setUpdater(userId);
                recent.setUpdaterName(MailSessionCtx.getCurrentUserName());
                dynamicDao.save(recent);
            }
        }
        evictOverflow(userId);
    }

    /** 清除当前用户全部最近收件人（逻辑删除），返回清除条数 */
    @Transactional(rollbackFor = Exception.class)
    public int clear() {
        List<MailContactRecent> all = listEntities();
        for (MailContactRecent recent : all) {
            recent.setDelStatus(1);
            recent.setDeleteAt(new Date());
            touch(recent);
            dynamicDao.save(recent);
        }
        return all.size();
    }

    // ==================== 容量淘汰 ====================

    /** 超出 {@value #RECENT_CAP} 的部分按 lastUsedAt 最旧逻辑删除 */
    private void evictOverflow(String userId) {
        MetaQuery query = MetaFactory.query(MailContactRecent.class)
                .where(Filter.eq("userId", userId), Filter.eq("delStatus", 0));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        List<MailContactRecent> all = rows.stream().map(this::toEntity).collect(Collectors.toList());
        for (MailContactRecent eviction : selectEvictions(all, RECENT_CAP)) {
            eviction.setDelStatus(1);
            eviction.setDeleteAt(new Date());
            touch(eviction);
            dynamicDao.save(eviction);
        }
    }

    /**
     * 淘汰选择（纯函数，单测用）：按 lastUsedAt 倒序保留 cap 条，其余返回。
     * lastUsedAt 相同按 id 字典序兜底，保证结果稳定。
     */
    static List<MailContactRecent> selectEvictions(List<MailContactRecent> all, int cap) {
        List<MailContactRecent> sorted = all.stream()
                .sorted(Comparator.comparing(MailContactRecent::getLastUsedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(MailContactRecent::getId,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        return sorted.size() <= cap ? List.of() : sorted.subList(cap, sorted.size());
    }

    // ==================== 响应转换 ====================

    static Map<String, Object> toResponse(MailContactRecent recent) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", recent.getId());
        map.put("email", recent.getEmail());
        if (recent.getName() != null) {
            map.put("name", recent.getName());
        }
        map.put("useCount", recent.getUseCount());
        map.put("lastUsedAt", recent.getLastUsedAt() == null
                ? "" : recent.getLastUsedAt().toInstant().toString());
        return map;
    }

    // ==================== 内部辅助 ====================

    private void touch(MailContactRecent recent) {
        recent.setUpdateAt(new Date());
        recent.setUpdater(MailSessionCtx.getCurrentUserId());
        recent.setUpdaterName(MailSessionCtx.getCurrentUserName());
    }

    /** 查询行转实体（MetaQuery 返回 Map，字段名 camelCase） */
    private MailContactRecent toEntity(Map<String, Object> row) {
        MailContactRecent recent = new MailContactRecent();
        recent.setId(str(row.get("id")));
        recent.setUserId(str(row.get("userId")));
        recent.setEmail(str(row.get("email")));
        recent.setName(str(row.get("name")));
        Object useCount = row.get("useCount");
        recent.setUseCount(useCount instanceof Number n ? n.intValue() : 0);
        recent.setLastUsedAt(MailMessageService.toDate(row.get("lastUsedAt")));
        recent.setTenantCode(str(row.get("tenantCode")));
        Object delStatus = row.get("delStatus");
        recent.setDelStatus(delStatus instanceof Number n ? n.intValue() : 0);
        recent.setCreateAt(MailMessageService.toDate(row.get("createAt")));
        recent.setUpdateAt(MailMessageService.toDate(row.get("updateAt")));
        recent.setCreator(str(row.get("creator")));
        recent.setCreatorName(str(row.get("creatorName")));
        recent.setUpdater(str(row.get("updater")));
        recent.setUpdaterName(str(row.get("updaterName")));
        return recent;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
