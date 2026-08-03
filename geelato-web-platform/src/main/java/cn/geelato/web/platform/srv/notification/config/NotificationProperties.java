package cn.geelato.web.platform.srv.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 通知中心配置（community 站内信投递闭环）。
 * <p>
 * 仅服务于 community 自身的站内信投递调度。
 * 外部渠道（邮件/短信/企业微信）由统一消息中心 geelato-message 负责，
 * geelato-message 投递站内信时调用 community 的 /api/notification/send 落地，
 * 与本配置无关。
 *
 * @author geelato
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "geelato.notification")
public class NotificationProperties {

    /** outbox 投递调度间隔（毫秒），默认 3s */
    private long outboxIntervalMs = 3000L;

    /** 单次扫描处理的 outbox 条数上限 */
    private int outboxBatchSize = 50;

    /** 最大重试次数，达上限进入死信 */
    private int maxRetryCount = 5;
}
