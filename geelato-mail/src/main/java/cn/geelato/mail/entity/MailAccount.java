package cn.geelato.mail.entity;

import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 邮箱账户实体（用户配置的外部邮箱：IMAP/SMTP 服务器 + AES-GCM 加密凭据）。
 *
 * 表名 mail_account，按 user_id 数据隔离（每用户可配多个外部邮箱）。
 * 凭据安全：passwordCipher 由 MailCryptoService（AES-256-GCM）加密落库，
 * KEK 来自配置 geelato.mail.kek（env GEELATO_MAIL_KEK），未配置时写入 fail-fast。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@Getter
@Setter
@Entity(name = "mail_account", catalog = "mail")
@Title(title = "邮箱账户")
public class MailAccount extends BaseSortableEntity {

    /** 所属用户ID（数据隔离） */
    @Title(title = "所属用户ID")
    @Col(name = "user_id", nullable = false, charMaxlength = 32)
    private String userId;

    /** 账户显示名 */
    @Title(title = "账户显示名")
    @Col(name = "name", nullable = false, charMaxlength = 64)
    private String name;

    /** 邮箱地址 */
    @Title(title = "邮箱地址")
    @Col(name = "email", nullable = false, charMaxlength = 128)
    private String email;

    /** 头像URL */
    @Title(title = "头像URL")
    @Col(name = "avatar", charMaxlength = 512)
    private String avatar;

    /** 默认签名（HTML） */
    @Title(title = "默认签名")
    @Col(name = "signature", dataType = "text")
    private String signature;

    /** 是否默认账户 */
    @Title(title = "是否默认账户")
    @Col(name = "is_default", nullable = false, dataType = "int")
    private int isDefault;

    /** 服务商（gmail/qq/163/outlook/custom） */
    @Title(title = "服务商", description = "gmail/qq/163/outlook/custom")
    @Col(name = "provider_code", charMaxlength = 32)
    private String providerCode;

    /** 收信协议（imap/pop3） */
    @Title(title = "收信协议", description = "imap/pop3")
    @Col(name = "incoming_protocol", nullable = false, charMaxlength = 8)
    private String incomingProtocol = "imap";

    /** 收信服务器主机 */
    @Title(title = "收信服务器主机")
    @Col(name = "incoming_host", nullable = false, charMaxlength = 128)
    private String incomingHost;

    /** 收信服务器端口 */
    @Title(title = "收信服务器端口")
    @Col(name = "incoming_port", nullable = false, dataType = "int")
    private int incomingPort;

    /** 收信加密（ssl/tls/none） */
    @Title(title = "收信加密", description = "ssl/tls/none")
    @Col(name = "incoming_encryption", nullable = false, charMaxlength = 8)
    private String incomingEncryption = "ssl";

    /** 发信(SMTP)服务器主机 */
    @Title(title = "发信服务器主机")
    @Col(name = "outgoing_host", nullable = false, charMaxlength = 128)
    private String outgoingHost;

    /** 发信(SMTP)服务器端口 */
    @Title(title = "发信服务器端口")
    @Col(name = "outgoing_port", nullable = false, dataType = "int")
    private int outgoingPort;

    /** 发信加密（ssl/tls/none） */
    @Title(title = "发信加密", description = "ssl/tls/none")
    @Col(name = "outgoing_encryption", nullable = false, charMaxlength = 8)
    private String outgoingEncryption = "ssl";

    /** 登录用户名（通常同邮箱） */
    @Title(title = "登录用户名")
    @Col(name = "username", nullable = false, charMaxlength = 128)
    private String username;

    /** 邮箱密码/授权码密文（AES-256-GCM，Base64） */
    @Title(title = "密码密文")
    @Col(name = "password_cipher", nullable = false, dataType = "text")
    private String passwordCipher;

    /** 最近同步时间 */
    @Title(title = "最近同步时间")
    @Col(name = "last_sync_at")
    private Date lastSyncAt;

    /** 最近同步结果（syncing/success/failed） */
    @Title(title = "最近同步结果", description = "syncing/success/failed")
    @Col(name = "last_sync_status", charMaxlength = 16)
    private String lastSyncStatus;

    /** 是否开启定时同步（1=开启，由 geelato.mail.sync.enabled 全局开关与该账号开关共同控制） */
    @Title(title = "定时同步开关", description = "1=开启")
    @Col(name = "sync_enabled")
    private int syncEnabled;

    /** 定时同步间隔（分钟） */
    @Title(title = "定时同步间隔(分钟)")
    @Col(name = "sync_interval_minutes")
    private Integer syncIntervalMinutes;
}
