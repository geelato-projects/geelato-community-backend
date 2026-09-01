package cn.geelato.mail.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.query.Filter;
import cn.geelato.orm.query.MetaQuery;
import cn.geelato.mail.entity.MailVacation;
import cn.geelato.mail.util.MailSessionCtx;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 邮件假期自动回复配置服务（每用户一行，upsert 语义）。
 *
 * <p>契约：GET /api/mail/vacation-reply 返回完整 MailVacationReply
 * （enabled/subject/content/onlyContacts 必填，startTime/endTime/lastSentAt 可选，
 * 未配置过返回 {enabled:false, subject:'', content:'', onlyContacts:false} 默认快照）；
 * PUT 全量替换（前端提交完整 MailVacationReply 对象），落库前做长度/时间格式/先后校验
 * （fail-fast 40000）。
 *
 * <p>真实自动回复发送由 MailAutoReplyService 在收信钩子中执行（SMTP 通道 +
 * mail_auto_reply_log 每发件人 24h 去重台账），本服务持久化配置；lastSentAt 由引擎回写
 * （{@link #touchLastSentAt}），PUT 不允许客户端直接写 lastSentAt（忽略该字段，
 * 防止伪造回复台账）。
 *
 * 数据隔离：全部按当前登录用户 userId 过滤（每用户至多一行，upsert 保证）。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@Service
public class MailVacationService {

    /** 列宽上限（对齐 V79） */
    static final int MAX_SUBJECT_LEN = 256;
    static final int MAX_CONTENT_LEN = 20000;

    @Autowired
    @Qualifier("dynamicDao")
    private Dao dynamicDao;

    // ==================== 查询 ====================

    /** 当前用户假期回复配置（未配置返回默认快照 enabled=false） */
    public Map<String, Object> get() {
        MailVacation row = findOwned();
        return toResponse(row);
    }

    /** 查询当前用户配置行（无返回 null）；public 供单测 stub */
    public MailVacation findOwned() {
        MetaQuery query = MetaFactory.query(MailVacation.class)
                .where(Filter.eq("userId", MailSessionCtx.getCurrentUserId()),
                        Filter.eq("delStatus", 0));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        return toEntity(rows.get(0));
    }

    // ==================== 写 ====================

    /**
     * 全量替换假期回复配置（upsert：无行则建，有行则改）。
     *
     * @throws IllegalArgumentException 长度/时间格式/时间先后非法（调用方转 40000）
     */
    public void put(Boolean enabled, String subject, String content, Boolean onlyContacts,
                    String startTime, String endTime) {
        String normalizedSubject = subject == null ? "" : subject.trim();
        if (normalizedSubject.length() > MAX_SUBJECT_LEN) {
            throw new IllegalArgumentException(
                    "自动回复主题超长（上限 " + MAX_SUBJECT_LEN + " 字符，实际 " + normalizedSubject.length() + "）");
        }
        String normalizedContent = content == null ? "" : content;
        if (normalizedContent.length() > MAX_CONTENT_LEN) {
            throw new IllegalArgumentException(
                    "自动回复正文超长（上限 " + MAX_CONTENT_LEN + " 字符，实际 " + normalizedContent.length() + "）");
        }
        Date start = parseIsoInstant(startTime, "startTime");
        Date end = parseIsoInstant(endTime, "endTime");
        if (start != null && end != null && start.after(end)) {
            throw new IllegalArgumentException("假期开始时间不能晚于结束时间");
        }

        String userId = MailSessionCtx.getCurrentUserId();
        String userName = MailSessionCtx.getCurrentUserName();
        Date now = new Date();
        MailVacation row = findOwned();
        if (row == null) {
            row = new MailVacation();
            row.setUserId(userId);
            row.setTenantCode(MailSessionCtx.getCurrentTenantCode());
            row.setDelStatus(0);
            row.setCreateAt(now);
            row.setCreator(userId);
            row.setCreatorName(userName);
        }
        row.setEnabled(Boolean.TRUE.equals(enabled) ? 1 : 0);
        row.setSubject(normalizedSubject);
        row.setContent(normalizedContent);
        row.setOnlyContacts(Boolean.TRUE.equals(onlyContacts) ? 1 : 0);
        row.setStartTime(start);
        row.setEndTime(end);
        // lastSentAt 仅引擎回写，PUT 不触碰
        row.setUpdateAt(now);
        row.setUpdater(userId);
        row.setUpdaterName(userName);
        dynamicDao.save(row);
    }

    /**
     * 引擎回写最近一次自动回复时间（休假回复发送成功后由 MailAutoReplyService 调用）。
     * 未配置过假期回复时不建仓（无配置则无展示诉求）。
     */
    public void touchLastSentAt(Date sentAt) {
        MailVacation row = findOwned();
        if (row == null) {
            return;
        }
        row.setLastSentAt(sentAt);
        row.setUpdateAt(new Date());
        row.setUpdater(MailSessionCtx.getCurrentUserId());
        row.setUpdaterName(MailSessionCtx.getCurrentUserName());
        dynamicDao.save(row);
    }

    // ==================== 响应转换 ====================

    /** 转前端 MailVacationReply 契约（row 为 null 返回默认快照） */
    public Map<String, Object> toResponse(MailVacation row) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (row == null) {
            map.put("enabled", false);
            map.put("subject", "");
            map.put("content", "");
            map.put("onlyContacts", false);
            return map;
        }
        map.put("enabled", row.getEnabled() == 1);
        map.put("subject", row.getSubject() == null ? "" : row.getSubject());
        map.put("content", row.getContent() == null ? "" : row.getContent());
        map.put("onlyContacts", row.getOnlyContacts() == 1);
        if (row.getStartTime() != null) {
            map.put("startTime", row.getStartTime().toInstant().toString());
        }
        if (row.getEndTime() != null) {
            map.put("endTime", row.getEndTime().toInstant().toString());
        }
        if (row.getLastSentAt() != null) {
            map.put("lastSentAt", row.getLastSentAt().toInstant().toString());
        }
        return map;
    }

    // ==================== 内部辅助 ====================

    /** ISO-8601 时间串解析（null/空白 → null；非法格式 fail-fast） */
    private Date parseIsoInstant(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Date.from(Instant.parse(value.trim()));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    field + " 时间格式非法（需 ISO-8601，如 2026-08-12T00:00:00Z）: " + value);
        }
    }

    /** 查询行转实体（MetaQuery 返回 Map，字段名 camelCase） */
    private MailVacation toEntity(Map<String, Object> row) {
        MailVacation vacation = new MailVacation();
        vacation.setId(str(row.get("id")));
        vacation.setUserId(str(row.get("userId")));
        Object enabled = row.get("enabled");
        vacation.setEnabled(enabled instanceof Number n ? n.intValue() : 0);
        vacation.setSubject(str(row.get("subject")));
        vacation.setContent(str(row.get("content")));
        Object onlyContacts = row.get("onlyContacts");
        vacation.setOnlyContacts(onlyContacts instanceof Number n ? n.intValue() : 0);
        // MetaQuery.list() 对 datetime 列返回 LocalDateTime，须经 toDate 转换（直接 instanceof Date 会静默丢值）
        vacation.setStartTime(MailMessageService.toDate(row.get("startTime")));
        vacation.setEndTime(MailMessageService.toDate(row.get("endTime")));
        vacation.setLastSentAt(MailMessageService.toDate(row.get("lastSentAt")));
        vacation.setTenantCode(str(row.get("tenantCode")));
        Object delStatus = row.get("delStatus");
        vacation.setDelStatus(delStatus instanceof Number n ? n.intValue() : 0);
        vacation.setCreateAt(MailMessageService.toDate(row.get("createAt")));
        vacation.setUpdateAt(MailMessageService.toDate(row.get("updateAt")));
        vacation.setCreator(str(row.get("creator")));
        vacation.setCreatorName(str(row.get("creatorName")));
        vacation.setUpdater(str(row.get("updater")));
        vacation.setUpdaterName(str(row.get("updaterName")));
        return vacation;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
