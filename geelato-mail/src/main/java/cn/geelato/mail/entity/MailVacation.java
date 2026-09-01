package cn.geelato.mail.entity;

import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 假期自动回复配置实体（每用户一行，upsert 语义由 Service 保证）。
 *
 * 表名 mail_vacation。真实自动回复发送由 MailAutoReplyService 在收信钩子中执行
 * （SMTP 通道 + mail_auto_reply_log 每发件人 24h 去重台账），本表持久化配置；
 * last_sent_at 由引擎回写最近一次自动回复时间（设置页展示用）。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@Getter
@Setter
@Entity(name = "mail_vacation", catalog = "mail")
@Title(title = "邮件假期自动回复配置")
public class MailVacation extends BaseSortableEntity {

    /** 所属用户ID（数据隔离） */
    @Title(title = "所属用户ID")
    @Col(name = "user_id", nullable = false, charMaxlength = 32)
    private String userId;

    /** 是否启用 */
    @Title(title = "是否启用")
    @Col(name = "enabled", nullable = false, dataType = "int")
    private int enabled;

    /** 自动回复主题 */
    @Title(title = "自动回复主题")
    @Col(name = "subject", charMaxlength = 256)
    private String subject;

    /** 自动回复正文 */
    @Title(title = "自动回复正文")
    @Col(name = "content", dataType = "text")
    private String content;

    /** 仅回复联系人 */
    @Title(title = "仅回复联系人")
    @Col(name = "only_contacts", nullable = false, dataType = "int")
    private int onlyContacts;

    /** 假期开始时间（NULL=立即生效） */
    @Title(title = "假期开始时间")
    @Col(name = "start_time")
    private Date startTime;

    /** 假期结束时间（NULL=不限） */
    @Title(title = "假期结束时间")
    @Col(name = "end_time")
    private Date endTime;

    /** 最近一次自动回复时间（引擎回写，预留） */
    @Title(title = "最近自动回复时间")
    @Col(name = "last_sent_at")
    private Date lastSentAt;
}
