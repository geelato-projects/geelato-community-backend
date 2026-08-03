package cn.geelato.web.platform.srv.notification.enums;

/**
 * 通知发送者类型。
 *
 * @author geelato
 */
public enum SenderTypeEnum {

    /** 系统发送（定时任务、事件触发等），senderId 为 system */
    SYSTEM("system"),
    /** 用户发送（人工主动发起），senderId 为发送者 userId */
    USER("user");

    private final String value;

    SenderTypeEnum(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
