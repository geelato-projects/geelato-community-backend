package cn.geelato.mail.entity;

import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 邮件自动回复发送台账实体（过滤器 autoReply 动作 + 假期自动回复）。
 *
 * 表名 mail_auto_reply_log。用途：① 每发件人 24 小时频率上限判断
 * （取 user_id + sender_email + reply_type + ref_id 维度最近一次 sentAt）；
 * ② 发送审计留痕（append-only，不更新既有行）。
 *
 * ref_id 空串语义：replyType='filter' 时为 mail_filter.id（同发件人命中不同过滤器
 * 各自独立计频）；replyType='vacation' 时固定空串（休假回复全局限一）。
 * sender_email 小写归一化落库（频率判断大小写不敏感）。
 *
 * 关联文档：.geelato/plans/2026-08-14-mail-module-completion.md（Step B1）
 */
@Getter
@Setter
@Entity(name = "mail_auto_reply_log", catalog = "mail")
@Title(title = "邮件自动回复发送台账")
public class MailAutoReplyLog extends BaseSortableEntity {

    /** 所属用户ID（数据隔离） */
    @Title(title = "所属用户ID")
    @Col(name = "user_id", nullable = false, charMaxlength = 32)
    private String userId;

    /** 发件人地址（小写归一化） */
    @Title(title = "发件人地址")
    @Col(name = "sender_email", nullable = false, charMaxlength = 128)
    private String senderEmail;

    /** 回复类型（filter=过滤器autoReply动作；vacation=假期自动回复） */
    @Title(title = "回复类型")
    @Col(name = "reply_type", nullable = false, charMaxlength = 16)
    private String replyType;

    /** 关联ID（filter=mail_filter.id；vacation=空串） */
    @Title(title = "关联ID")
    @Col(name = "ref_id", nullable = false, charMaxlength = 32)
    private String refId;

    /** 触发回复的收件邮件ID（mail_message.id） */
    @Title(title = "触发邮件ID")
    @Col(name = "mail_id", charMaxlength = 32)
    private String mailId;

    /** 回复主题 */
    @Title(title = "回复主题")
    @Col(name = "reply_subject", charMaxlength = 256)
    private String replySubject;

    /** 发送时间 */
    @Title(title = "发送时间")
    @Col(name = "sent_at", nullable = false)
    private Date sentAt;
}
