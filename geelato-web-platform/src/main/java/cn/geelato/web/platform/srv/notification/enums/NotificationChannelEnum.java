package cn.geelato.web.platform.srv.notification.enums;

/**
 * community 站内信投递渠道。
 * <p>
 * 站内信（INAPP）是 community 通知中心内置的投递渠道：写收件人状态表 + SSE 实时推送。
 * <p>
 * 架构说明：geelato-message 是统一消息中心，站内信与邮件/短信/企业微信平级，是其一个投递渠道（type=inapp）。
 * geelato-message 投递站内信时，通过其 InAppClient 调用 community 的 /api/notification/send 落地。
 * community 这里的 INAPP 渠道服务于 community 自身的站内信投递闭环（事件/REST 触发），
 * 与 geelato-message 互不依赖。
 * <p>
 * DeliveryChannel SPI 支持未来扩展其他内置渠道（如 community 自带邮件）。
 *
 * @author geelato
 */
public enum NotificationChannelEnum {

    /** 站内信：写收件人状态表 + SSE 实时推送，内置默认 */
    INAPP("inapp");

    private final String value;

    NotificationChannelEnum(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
