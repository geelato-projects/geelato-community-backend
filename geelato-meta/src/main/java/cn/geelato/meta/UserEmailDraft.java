package cn.geelato.meta;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Entity(name = "platform_user_email_draft", catalog = "platform")
@TableName("platform_user_email_draft")
@Title(title = "用户邮件草稿")
public class UserEmailDraft extends BaseEntity {
    @Title(title = "用户ID")
    @Col(name = "user_id", nullable = false)
    private String userId;

    @Title(title = "邮箱账号ID")
    @Col(name = "email_account_id", nullable = false)
    private String emailAccountId;

    @Title(title = "发件显示名称")
    @Col(name = "from_name")
    private String fromName;

    @Title(title = "主题")
    private String subject;

    @Title(title = "收件人JSON")
    @Col(name = "to_json")
    private String toJson;

    @Title(title = "抄送JSON")
    @Col(name = "cc_json")
    private String ccJson;

    @Title(title = "密送JSON")
    @Col(name = "bcc_json")
    private String bccJson;

    @Title(title = "正文类型")
    @Col(name = "body_type")
    private String bodyType;

    @Title(title = "纯文本正文")
    @Col(name = "text_body")
    private String textBody;

    @Title(title = "HTML正文")
    @Col(name = "html_body")
    private String htmlBody;

    @Title(title = "附件ID JSON")
    @Col(name = "attachment_ids_json")
    private String attachmentIdsJson;

    @Title(title = "邮件附件引用JSON")
    @Col(name = "mail_attachment_refs_json")
    private String mailAttachmentRefsJson;

    @Title(title = "来源邮件ID")
    @Col(name = "source_mail_id")
    private String sourceMailId;

    @Title(title = "撰写模式")
    @Col(name = "compose_mode")
    private String composeMode;

    @Title(title = "In-Reply-To")
    @Col(name = "in_reply_to_message_id")
    private String inReplyToMessageId;

    @Title(title = "References Header")
    @Col(name = "references_header")
    private String referencesHeader;

    @Title(title = "自动保存时间")
    @Col(name = "auto_save_at")
    private Date autoSaveAt;

    @Title(title = "发送状态", description = "draft/sent")
    @Col(name = "send_status")
    private String sendStatus;

    @Title(title = "启用状态", description = "1启用，0不启用")
    @Col(name = "enable_status")
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;
}
