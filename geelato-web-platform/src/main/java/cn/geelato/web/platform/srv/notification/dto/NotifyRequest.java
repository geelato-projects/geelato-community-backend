package cn.geelato.web.platform.srv.notification.dto;

import cn.geelato.web.platform.srv.notification.enums.NotificationChannelEnum;
import cn.geelato.web.platform.srv.notification.enums.SenderTypeEnum;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 发起一条通知的请求模型。业务方通过 {@code EventPublisher.publish(new NotifyEvent(this, request))} 触发，
 * 也可由 {@code NotificationController} 的 /send 接口直接调用。
 * <p>
 * Phase 1：recipients 为显式 userId 列表（务实），channels 默认仅站内信。
 * 渠道与收件人正交：每个渠道都会向全部 recipients 投递。
 *
 * @author geelato
 */
@Getter
@Setter
public class NotifyRequest {

    /** 收件人 userId 列表（必填） */
    private List<String> recipients;
    /** 投递渠道，为空时默认仅站内信 [inapp] */
    private List<String> channels;

    /** 标题（直传；模板渲染后续 Phase 支持） */
    private String title;
    /** 内容（直传；模板渲染后续 Phase 支持） */
    private String content;

    /** 发送者 userId；为空时取当前登录用户，无登录上下文则 system */
    private String senderId;
    /** 发送者名称 */
    private String senderName;
    /** 发送者类型，默认 USER；系统触发应显式设为 SYSTEM */
    private String senderType;

    /** 业务类型：order/contract/task 等，用于业务幂等与归类 */
    private String bizType;
    /** 业务主键，配合 bizType 构成幂等键 */
    private String bizId;
    /** 点击跳转地址（前端 router.push / window.open） */
    private String actionUrl;

    /** 优先级，默认 0 */
    private int priority;

    /** 业务幂等键，为空时按 tenant+bizType+bizId 生成 */
    private String idempotencyKey;

    public static NotifyRequest of(List<String> recipients, String title, String content) {
        NotifyRequest req = new NotifyRequest();
        req.recipients = recipients;
        req.title = title;
        req.content = content;
        return req;
    }

    /** 解析有效渠道，为空时默认仅站内信 */
    public List<String> resolveChannels() {
        if (channels == null || channels.isEmpty()) {
            return List.of(NotificationChannelEnum.INAPP.value());
        }
        return channels;
    }

    public SenderTypeEnum resolveSenderType() {
        if (senderType == null) {
            return SenderTypeEnum.USER;
        }
        for (SenderTypeEnum e : SenderTypeEnum.values()) {
            if (e.value().equalsIgnoreCase(senderType)) {
                return e;
            }
        }
        return SenderTypeEnum.USER;
    }
}
