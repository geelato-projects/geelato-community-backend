package cn.geelato.meta;

import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

/**
 * 邮件附件本地存储实体
 * <p>数据源：PostgreSQL</p>
 * <p>数据源由 {@code EmailDataSourceConfig} 通过 application.properties 配置自动注册，
 * 无需在 platform_dev_db_connect 表中插入记录。</p>
 *
 * @see cn.geelato.web.platform.srv.email.config.EmailDataSourceConfig
 */
@Getter
@Setter
@Entity(name = "platform_email_attachment")
@Title(title = "邮件附件")
public class EmailAttachment extends BaseEntity {

    @Title(title = "邮件消息ID")
    @Col(name = "email_message_id", nullable = false, charMaxlength = 64)
    private String emailMessageId;

    @Title(title = "IMAP Part 序号")
    @Col(name = "part_id", charMaxlength = 32)
    private String partId;

    @Title(title = "文件名")
    @Col(name = "file_name", charMaxlength = 512)
    private String fileName;

    @Title(title = "MIME类型")
    @Col(name = "content_type", charMaxlength = 255)
    private String contentType;

    @Title(title = "附件大小(bytes)")
    @Col(name = "size")
    private Long size;

    @Title(title = "是否内联", description = "0否，1是")
    @Col(name = "is_inline", dataType = "smallint")
    private int inline;

    @Title(title = "Content-ID")
    @Col(name = "content_id", charMaxlength = 255)
    private String contentId;

    @Title(title = "平台Attachment表ID")
    @Col(name = "attachment_id", charMaxlength = 64)
    private String attachmentId;

    @Title(title = "OSS存储路径")
    @Col(name = "oss_path", charMaxlength = 1024)
    private String ossPath;
}
