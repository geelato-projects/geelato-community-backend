package cn.geelato.meta.email;

import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 邮件同步日志实体
 * <p>数据源：PostgreSQL</p>
 * <p>数据源由 {@code EmailDataSourceConfig} 通过 application.properties 配置自动注册，
 * 无需在 platform_dev_db_connect 表中插入记录。</p>
 *
 * @see cn.geelato.web.platform.srv.email.config.EmailDataSourceConfig
 */
@Getter
@Setter
@Entity(name = "platform_email_sync_log",catalog = "email")
@Title(title = "邮件同步日志")
public class EmailSyncLog extends BaseEntity {

    @Title(title = "邮箱账号ID")
    @Col(name = "email_account_id", nullable = false, charMaxlength = 64)
    private String emailAccountId;

    @Title(title = "文件夹名称")
    @Col(name = "folder", charMaxlength = 255)
    private String folder;

    @Title(title = "同步类型", description = "full/incremental")
    @Col(name = "sync_type", charMaxlength = 32)
    private String syncType;

    @Title(title = "同步状态", description = "running/success/error")
    @Col(name = "status", charMaxlength = 32)
    private String status;

    @Title(title = "本次最大UID")
    @Col(name = "last_uid")
    private Long lastUid;

    @Title(title = "IMAP远端邮件总数")
    @Col(name = "total_count")
    private Integer totalCount;

    @Title(title = "同步邮件数量")
    @Col(name = "synced_count")
    private Integer syncedCount;

    @Title(title = "错误信息")
    @Col(name = "error_message", dataType = "text")
    private String errorMessage;

    @Title(title = "开始时间")
    @Col(name = "start_at")
    private Date startAt;

    @Title(title = "结束时间")
    @Col(name = "end_at")
    private Date endAt;
}
