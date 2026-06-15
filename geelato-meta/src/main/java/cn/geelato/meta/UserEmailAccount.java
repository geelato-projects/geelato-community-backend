package cn.geelato.meta;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Entity(name = "platform_user_email_account")
@Title(title = "用户邮箱账号")
public class UserEmailAccount extends BaseEntity implements EntityEnableAble {
    @Title(title = "用户ID")
    @Col(name = "user_id", nullable = false)
    private String userId;

    @Title(title = "邮箱地址")
    @Col(name = "email_address", nullable = false)
    private String emailAddress;

    @Title(title = "显示名称")
    @Col(name = "display_name")
    private String displayName;

    @Title(title = "邮箱厂商", description = "qq/163/126/gmail/outlook/other")
    @Col(name = "provider_code")
    private String providerCode;

    @Title(title = "默认邮箱", description = "0否，1是")
    @Col(name = "default_flag")
    private int defaultFlag;

    @Title(title = "IMAP Host")
    @Col(name = "imap_host")
    private String imapHost;

    @Title(title = "IMAP 端口")
    @Col(name = "imap_port")
    private Integer imapPort;

    @Title(title = "IMAP SSL", description = "0否，1是")
    @Col(name = "imap_ssl")
    private int imapSsl;

    @Title(title = "默认文件夹")
    @Col(name = "imap_folder_default")
    private String imapFolderDefault;

    @Title(title = "认证类型", description = "auth_code/password/oauth2")
    @Col(name = "auth_type")
    private String authType;

    @Title(title = "认证用户名")
    @Col(name = "auth_user")
    private String authUser;

    @Title(title = "认证密文", description = "授权码/密码密文")
    @Col(name = "auth_secret")
    private String authSecret;

    @Title(title = "OAuth2 信息", description = "JSON")
    @Col(name = "oauth2_json")
    private String oauth2Json;

    @Title(title = "SMTP Host")
    @Col(name = "smtp_host")
    private String smtpHost;

    @Title(title = "SMTP 端口")
    @Col(name = "smtp_port")
    private Integer smtpPort;

    @Title(title = "SMTP SSL", description = "0否，1是")
    @Col(name = "smtp_ssl")
    private int smtpSsl;

    @Title(title = "SMTP STARTTLS", description = "0否，1是")
    @Col(name = "smtp_starttls")
    private int smtpStarttls;

    @Title(title = "SMTP 认证用户名")
    @Col(name = "smtp_auth_user")
    private String smtpAuthUser;

    @Title(title = "SMTP 认证密文", description = "授权码/密码密文")
    @Col(name = "smtp_auth_secret")
    private String smtpAuthSecret;

    @Title(title = "发件显示名称")
    @Col(name = "smtp_from_name")
    private String smtpFromName;

    @Title(title = "邮件签名", description = "HTML")
    @Col(name = "signature_html")
    private String signatureHtml;

    @Title(title = "启用状态", description = "1启用，0不启用")
    @Col(name = "enable_status")
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;

    @Title(title = "同步开关", description = "0关，1开")
    @Col(name = "sync_enabled")
    private int syncEnabled;

    @Title(title = "同步间隔(分钟)", description = "默认5分钟")
    @Col(name = "sync_interval_minutes")
    private Integer syncIntervalMinutes;

    @Title(title = "上次同步时间")
    @Col(name = "last_sync_at")
    private Date lastSyncAt;

    @Title(title = "同步状态", description = "idle/syncing/error")
    @Col(name = "sync_status")
    private String syncStatus;
}
