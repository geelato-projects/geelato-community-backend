package cn.geelato.meta.email;

import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 邮件同步本地存储实体
 *
 */
@Getter
@Setter
@Entity(name = "platform_email_message",catalog = "email")
@Title(title = "邮件消息")
public class EmailMessage extends BaseEntity {

    @Title(title = "邮箱账号ID")
    @Col(name = "email_account_id", nullable = false, charMaxlength = 64)
    private String emailAccountId;

    @Title(title = "用户ID")
    @Col(name = "user_id", nullable = false, charMaxlength = 64)
    private String userId;

    @Title(title = "文件夹名称")
    @Col(name = "folder", nullable = false, charMaxlength = 255)
    private String folder;

    @Title(title = "IMAP UID")
    @Col(name = "uid")
    private Long uid;

    @Title(title = "IMAP UIDVALIDITY")
    @Col(name = "uid_validity")
    private Long uidValidity;

    @Title(title = "RFC Message-ID")
    @Col(name = "message_id", charMaxlength = 512)
    private String messageId;

    @Title(title = "主题")
    @Col(name = "subject", charMaxlength = 1000)
    private String subject;

    @Title(title = "发件人JSON")
    @Col(name = "from_json", dataType = "text")
    private String fromJson;

    @Title(title = "收件人JSON")
    @Col(name = "to_json", dataType = "text")
    private String toJson;

    @Title(title = "抄送JSON")
    @Col(name = "cc_json", dataType = "text")
    private String ccJson;

    @Title(title = "密送JSON")
    @Col(name = "bcc_json", dataType = "text")
    private String bccJson;

    @Title(title = "发送时间")
    @Col(name = "sent_at")
    private Date sentAt;

    @Title(title = "接收时间")
    @Col(name = "received_at")
    private Date receivedAt;

    @Title(title = "邮件大小(bytes)")
    @Col(name = "size")
    private Long size;

    @Title(title = "未读标记", description = "0已读，1未读")
    @Col(name = "unread", dataType = "smallint")
    private int unread;

    @Title(title = "是否有附件", description = "0否，1是")
    @Col(name = "has_attachments", dataType = "smallint")
    private int hasAttachments;

    @Title(title = "纯文本正文")
    @Col(name = "text_body", dataType = "text")
    private String textBody;

    @Title(title = "HTML正文")
    @Col(name = "html_body", dataType = "text")
    private String htmlBody;

    @Title(title = "摘要")
    @Col(name = "snippet", charMaxlength = 500)
    private String snippet;

    @Title(title = "同步时间")
    @Col(name = "synced_at")
    private Date syncedAt;
}
