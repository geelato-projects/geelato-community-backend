package cn.geelato.mail.entity;

import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 邮件本地读模型实体（IMAP 同步落库；SMTP 发送后存发件箱副本）。
 *
 * 表名 mail_message。读路径（列表/详情）全部读本地表，不穿透 IMAP。
 * 同步去重：uk(account_id, folder, imap_uid)。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@Getter
@Setter
@Entity(name = "mail_message", catalog = "mail")
@Title(title = "邮件")
public class MailMessage extends BaseSortableEntity {

    /** 所属邮箱账户ID（mail_account.id） */
    @Title(title = "邮箱账户ID")
    @Col(name = "account_id", nullable = false, charMaxlength = 32)
    private String accountId;

    /** 所属用户ID（冗余，便于隔离查询） */
    @Title(title = "所属用户ID")
    @Col(name = "user_id", nullable = false, charMaxlength = 32)
    private String userId;

    /** RFC Message-ID */
    @Title(title = "RFC Message-ID")
    @Col(name = "message_id", charMaxlength = 256)
    private String messageId;

    /** RFC In-Reply-To 头（父邮件 Message-ID；无则回退 References 链末位；V82 会话视图归组依据） */
    @Title(title = "父邮件 Message-ID")
    @Col(name = "in_reply_to", charMaxlength = 512)
    private String inReplyTo;

    /** IMAP UID（同步去重依据） */
    @Title(title = "IMAP UID")
    @Col(name = "imap_uid", charMaxlength = 32)
    private String imapUid;

    /** 文件夹（inbox/sent/drafts/trash/spam/archive/custom:{id}） */
    @Title(title = "文件夹", description = "inbox/sent/drafts/trash/spam/archive/custom:{id}")
    @Col(name = "folder", nullable = false, charMaxlength = 64)
    private String folder = "inbox";

    /** 主题 */
    @Title(title = "主题")
    @Col(name = "subject", charMaxlength = 512)
    private String subject;

    /** 发件人显示名 */
    @Title(title = "发件人显示名")
    @Col(name = "from_name", charMaxlength = 128)
    private String fromName;

    /** 发件人地址 */
    @Title(title = "发件人地址")
    @Col(name = "from_email", nullable = false, charMaxlength = 128)
    private String fromEmail;

    /** 收件人JSON数组 [{name,email}] */
    @Title(title = "收件人JSON")
    @Col(name = "to_json", dataType = "text")
    private String toJson;

    /** 抄送JSON数组 */
    @Title(title = "抄送JSON")
    @Col(name = "cc_json", dataType = "text")
    private String ccJson;

    /** 密送JSON数组 */
    @Title(title = "密送JSON")
    @Col(name = "bcc_json", dataType = "text")
    private String bccJson;

    /** 列表预览文本（正文摘要） */
    @Title(title = "预览文本")
    @Col(name = "preview", charMaxlength = 512)
    private String preview;

    /** HTML正文 */
    @Title(title = "HTML正文")
    @Col(name = "content_html", dataType = "text")
    private String contentHtml;

    /** 纯文本正文 */
    @Title(title = "纯文本正文")
    @Col(name = "content_text", dataType = "text")
    private String contentText;

    /** 邮件日期（发送/接收时间） */
    @Title(title = "邮件日期")
    @Col(name = "send_date", nullable = false)
    private Date sendDate;

    /** 已读状态（read/unread） */
    @Title(title = "已读状态", description = "read/unread")
    @Col(name = "read_status", nullable = false, charMaxlength = 8)
    private String readStatus = "unread";

    /** 标记JSON数组（starred/todo/done 等） */
    @Title(title = "标记JSON")
    @Col(name = "flags_json", charMaxlength = 256)
    private String flagsJson = "[]";

    /** 邮件大小（字节） */
    @Title(title = "邮件大小")
    @Col(name = "mail_size", nullable = false, dataType = "int")
    private int mailSize;

    /** 是否含附件 */
    @Title(title = "是否含附件")
    @Col(name = "has_attachment", nullable = false, dataType = "int")
    private int hasAttachment;

    /** 附件元数据JSON数组 [{name,size,contentType,url}] */
    @Title(title = "附件元数据JSON")
    @Col(name = "attachments_json", dataType = "text")
    private String attachmentsJson;

    /** 标签ID JSON数组（邮件-标签关联，元素为 mail_label.id；V75 新增） */
    @Title(title = "标签ID JSON")
    @Col(name = "label_ids", charMaxlength = 512)
    private String labelIds;

    /** 草稿扩展字段JSON（inReplyTo/signatureId/scheduleSendAt 等，写信页恢复用；V75 新增） */
    @Title(title = "草稿扩展字段JSON")
    @Col(name = "draft_ext_json", dataType = "text")
    private String draftExtJson;

    /** 发送状态（queued/sending/sent/failed；仅发件箱副本有值；V76 新增） */
    @Title(title = "发送状态", description = "queued/sending/sent/failed")
    @Col(name = "send_status", charMaxlength = 16)
    private String sendStatus;

    /** 发送失败原因（SMTP 错误摘要；V76 新增） */
    @Title(title = "发送失败原因")
    @Col(name = "send_error", charMaxlength = 512)
    private String sendError;

    /** 撤回状态（failed；SMTP 不支持真实撤回，withdrawn 预留给服务商级能力；V76 新增） */
    @Title(title = "撤回状态", description = "failed；withdrawn 预留")
    @Col(name = "withdraw_status", charMaxlength = 16)
    private String withdrawStatus;

    /** 用户备注 */
    @Title(title = "用户备注")
    @Col(name = "note", charMaxlength = 1024)
    private String note;

    /** 优先级（high/normal/low） */
    @Title(title = "优先级", description = "high/normal/low")
    @Col(name = "priority", charMaxlength = 8)
    private String priority = "normal";

    /** 是否草稿 */
    @Title(title = "是否草稿")
    @Col(name = "is_draft", nullable = false, dataType = "int")
    private int isDraft;
}
