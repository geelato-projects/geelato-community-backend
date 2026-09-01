package cn.geelato.mail.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 邮件模块配置（prefix=geelato.mail）。
 *
 * <ul>
 *   <li>{@code geelato.mail.auto-init-tables}：启动时自动在 mail 库建缺失表（默认 true）</li>
 *   <li>{@code geelato.mail.sync.enabled}：定时同步开关（默认 false，见 MailSyncScheduleTask）</li>
 *   <li>{@code geelato.mail.kek}：邮箱凭据主密钥（MailCryptoService，凭据读写时 fail-fast）</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "geelato.mail")
public class MailProperties {

    /** 启动时自动初始化 mail 库缺失表（幂等，已存在的表跳过） */
    private boolean autoInitTables = true;

    public boolean isAutoInitTables() {
        return autoInitTables;
    }

    public void setAutoInitTables(boolean autoInitTables) {
        this.autoInitTables = autoInitTables;
    }
}
