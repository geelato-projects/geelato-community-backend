package cn.geelato.mail.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.query.Filter;
import cn.geelato.orm.query.MetaQuery;
import cn.geelato.orm.query.Order;
import cn.geelato.mail.contact.service.MailContactService;
import cn.geelato.mail.entity.MailAccount;
import cn.geelato.mail.entity.MailAutoReplyLog;
import cn.geelato.mail.entity.MailFilter;
import cn.geelato.mail.entity.MailMessage;
import cn.geelato.mail.entity.MailVacation;
import cn.geelato.mail.util.MailSessionCtx;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 邮件自动回复发送服务（过滤器 autoReply 动作 + 假期自动回复）。
 *
 * <p>发送通道：经 {@link MailProtocolService#send} SMTP 真实发出（同步阻塞，
 * 由收信钩子 POST /mail/sync 触发链调用）。
 *
 * <p>频率上限：同一发件人 24 小时内仅回复一次（{@link #REPLY_INTERVAL_MS}），
 * 防止每次收信重复骚扰。频率维度：
 * <ul>
 *   <li>filter 类型：(user + sender + filterId) —— 同发件人命中不同过滤器各自独立计频</li>
 *   <li>vacation 类型：(user + sender) —— 休假回复全局限一（refId 固定空串）</li>
 * </ul>
 * 判断依据 mail_auto_reply_log 台账最近一次 sentAt；台账 append-only 写入，
 * 兼作发送审计留痕。休假回复另回写 mail_vacation.last_sent_at（设置页展示用）。
 *
 * <p>防回环：发件人地址为空或与当前账户邮箱相同（自发自收）时跳过；
 * 休假回复仅在启用 + 时间窗内 + 正文非空时触发，onlyContacts 开启时校验发件人在通讯录内。
 *
 * <p>失败语义：发送失败（SMTP 连接/认证/网络）记 error 日志并跳过，不阻断同步主流程
 * （自动回复为收信辅助动作；失败不伪造成功——台账仅在真实发送成功后写入）。
 *
 * 数据隔离：台账按当前登录用户 userId 写入与查询。
 *
 * 关联文档：.geelato/plans/2026-08-14-mail-module-completion.md（Step B1）
 */
@Slf4j
@Service
public class MailAutoReplyService {

    /** 同一发件人频率上限：24 小时（毫秒） */
    static final long REPLY_INTERVAL_MS = 24L * 60 * 60 * 1000;
    /** 回复类型：过滤器 autoReply 动作 */
    static final String TYPE_FILTER = "filter";
    /** 回复类型：假期自动回复 */
    static final String TYPE_VACATION = "vacation";

    @Autowired
    @Qualifier("dynamicDao")
    private Dao dynamicDao;

    @Autowired
    private MailProtocolService protocolService;

    @Autowired
    private MailVacationService vacationService;

    @Autowired
    private MailContactService contactService;

    // ==================== 过滤器 autoReply ====================

    /**
     * 过滤器 autoReply 动作命中：向发件人回复配置内容（主题 Re: 原主题）。
     *
     * <p>跳过条件：回复内容空白 / 发件人为空或系自己 / (sender + filterId) 24h 内已回复。
     * 发送失败记 error 日志不抛出（不阻断同步主流程）。
     */
    public void sendFilterReply(MailAccount account, String plainPassword, MailMessage msg,
                                MailFilter filter, String replyText) {
        String sender = normalize(msg.getFromEmail());
        if (replyText == null || replyText.isBlank() || shouldSkip(account, sender)) {
            return;
        }
        String refId = filter.getId() == null ? "" : filter.getId();
        if (withinInterval(sender, TYPE_FILTER, refId)) {
            return;
        }
        String subject = replySubject(null, msg.getSubject());
        if (doSend(account, plainPassword, sender, msg.getFromName(), subject, replyText,
                msg.getMessageId())) {
            writeLedger(sender, TYPE_FILTER, refId, msg.getId(), subject);
        }
    }

    // ==================== 假期自动回复 ====================

    /**
     * 假期自动回复批量入口（收信钩子每批调用一次）。
     *
     * <p>整批前置校验（配置启用 + 正文非空 + 时间窗内）只做一次配置查询；
     * onlyContacts 开启时整批预载一次联系人地址集。逐封跳过条件：发件人为空或系自己 /
     * 非联系人（onlyContacts）/ 24h 内已回复。发送成功回写 mail_vacation.last_sent_at。
     */
    public void sendVacationReplyBatch(MailAccount account, String plainPassword, List<MailMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        MailVacation cfg = vacationService.findOwned();
        if (cfg == null || cfg.getEnabled() != 1) {
            return;
        }
        String content = cfg.getContent();
        if (content == null || content.isBlank()) {
            return;
        }
        Date now = new Date();
        if (cfg.getStartTime() != null && now.before(cfg.getStartTime())) {
            return;
        }
        if (cfg.getEndTime() != null && now.after(cfg.getEndTime())) {
            return;
        }
        Set<String> contactEmails = cfg.getOnlyContacts() == 1 ? contactEmails() : Set.of();
        for (MailMessage msg : messages) {
            String sender = normalize(msg.getFromEmail());
            if (shouldSkip(account, sender)) {
                continue;
            }
            if (cfg.getOnlyContacts() == 1 && !contactEmails.contains(sender)) {
                continue;
            }
            if (withinInterval(sender, TYPE_VACATION, "")) {
                continue;
            }
            String subject = replySubject(cfg.getSubject(), msg.getSubject());
            if (doSend(account, plainPassword, sender, msg.getFromName(), subject, content,
                    msg.getMessageId())) {
                writeLedger(sender, TYPE_VACATION, "", msg.getId(), subject);
                vacationService.touchLastSentAt(new Date());
            }
        }
    }

    // ==================== 频率判断（台账） ====================

    /** 最近一条台账发送时间（无记录返回 null；public 供单测 stub） */
    public Date latestSentAt(String senderEmail, String replyType, String refId) {
        MetaQuery query = MetaFactory.query(MailAutoReplyLog.class)
                .where(Filter.eq("userId", MailSessionCtx.getCurrentUserId()),
                        Filter.eq("senderEmail", senderEmail),
                        Filter.eq("replyType", replyType),
                        Filter.eq("refId", refId),
                        Filter.eq("delStatus", 0))
                .order(Order.desc("sentAt"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = query.list();
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        // MetaQuery.list() 对 datetime 列返回 LocalDateTime，须经 toDate 转换
        return MailMessageService.toDate(rows.get(0).get("sentAt"));
    }

    /** 24h 频率窗口内已有回复记录 */
    private boolean withinInterval(String senderEmail, String replyType, String refId) {
        Date last = latestSentAt(senderEmail, replyType, refId);
        return last != null && System.currentTimeMillis() - last.getTime() < REPLY_INTERVAL_MS;
    }

    // ==================== 内部实现 ====================

    /** 防回环：发件人地址为空或与当前账户邮箱相同（自发自收）时跳过 */
    private boolean shouldSkip(MailAccount account, String senderEmail) {
        if (senderEmail == null || senderEmail.isBlank()) {
            return true;
        }
        return account.getEmail() != null && account.getEmail().equalsIgnoreCase(senderEmail);
    }

    /**
     * SMTP 真实发送（纯文本配置转 HTML：转义 + 换行转 br）。
     *
     * @return true=发送成功；false=失败（已记 error 日志，不抛出以保同步主流程）
     */
    private boolean doSend(MailAccount account, String plainPassword, String senderEmail,
                           String senderName, String subject, String text, String inReplyTo) {
        try {
            MailProtocolService.ComposeMail compose = new MailProtocolService.ComposeMail(
                    List.of(new MailProtocolService.ComposeMail.MailAddress(
                            senderName == null ? "" : senderName, senderEmail)),
                    List.of(), List.of(), subject, textToHtml(text), inReplyTo);
            protocolService.send(account, plainPassword, compose);
            log.info("自动回复已发送: account={}, sender={}, subject={}", account.getEmail(), senderEmail, subject);
            return true;
        } catch (Exception e) {
            // FALLBACK:[自动回复为收信辅助动作][发送失败记 error 日志并跳过，不阻断同步主流程；台账仅成功才写]
            log.error("自动回复发送失败（account={}, sender={}, subject={}）: {}",
                    account.getEmail(), senderEmail, subject, e.getMessage());
            return false;
        }
    }

    /** 写发送台账（append-only；频率判断与审计共用数据源） */
    private void writeLedger(String senderEmail, String replyType, String refId, String mailId, String subject) {
        String userId = MailSessionCtx.getCurrentUserId();
        String userName = MailSessionCtx.getCurrentUserName();
        Date now = new Date();
        MailAutoReplyLog entry = new MailAutoReplyLog();
        entry.setUserId(userId);
        entry.setSenderEmail(senderEmail);
        entry.setReplyType(replyType);
        entry.setRefId(refId);
        entry.setMailId(mailId);
        entry.setReplySubject(subject);
        entry.setSentAt(now);
        entry.setTenantCode(MailSessionCtx.getCurrentTenantCode());
        entry.setDelStatus(0);
        entry.setCreateAt(now);
        entry.setUpdateAt(now);
        entry.setCreator(userId);
        entry.setCreatorName(userName);
        entry.setUpdater(userId);
        entry.setUpdaterName(userName);
        dynamicDao.save(entry);
    }

    /** 回复主题：配置主题优先，缺省 Re: 原主题 */
    private String replySubject(String configured, String originalSubject) {
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return "Re: " + (originalSubject == null ? "" : originalSubject);
    }

    /** 纯文本转 HTML（转义特殊字符 + 换行转 br） */
    private String textToHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\r\n", "\n").replace("\n", "<br>");
    }

    /** 当前用户联系人地址集（小写归一化；onlyContacts 校验用） */
    private Set<String> contactEmails() {
        return contactService.listEntities(null).stream()
                .map(c -> normalize(c.getEmail()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /** 地址归一化（小写；频率判断大小写不敏感） */
    private String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
