package cn.geelato.web.platform.srv.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启用通知投递 outbox 调度。
 * {@code @EnableScheduling} 使 {@code NotificationOutboxScheduler} 的 @Scheduled 周期任务生效。
 *
 * @author geelato
 */
@Configuration
@EnableScheduling
public class NotificationSchedulingConfiguration {
}
