package cn.geelato.web.platform.srv.notification.channel;

import cn.geelato.meta.Notification;
import cn.geelato.web.platform.srv.notification.dto.ChannelResult;

import java.util.List;

/**
 * 投递渠道 SPI（可插拔投递方）。
 * <p>
 * 通知中心 = 消息管理 + 投递编排；每个渠道实现本接口，由 outbox 调度器在投递阶段按 channel 路由调用。
 * <ul>
 *   <li>{@link InAppChannel}：站内信，内置默认（写收件人状态表 + SSE 实时推送）</li>
 *   <li>geelato-message：作为 email/sms/wecom 渠道实现，在运行时通过 REST 接入独立运行的 geelato-message 服务，零编译期依赖</li>
 * </ul>
 * 新增渠道只需实现本接口并注册为 Spring Bean。
 *
 * @author geelato
 */
public interface DeliveryChannel {

    /**
     * 渠道标识，与 {@code NotificationChannelEnum} 的 value 对应（inapp/email/sms/wecom...）。
     */
    String getChannel();

    /**
     * 执行投递。
     *
     * @param notification 通知主体（含标题/内容/业务关联/跳转地址）
     * @param userIds      本渠道的目标收件人 userId 列表
     * @return 投递结果；success=true 时 outbox 标记成功，false 时进入重试/死信
     */
    ChannelResult deliver(Notification notification, List<String> userIds);
}
