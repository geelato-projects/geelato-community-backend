package cn.geelato.web.platform.srv.notification.enums;

import java.util.Optional;

/**
 * 站内信重要级别。
 * <p>
 * 对应 platform_notification.priority（tinyint），值域固定四级；发送侧对非法值硬失败，
 * 消费侧支持 priorityGe（>=某级）过滤与按级别从高到低排序。
 *
 * @author geelato
 */
public enum NotificationPriorityEnum {

    /** 普通：默认级别 */
    NORMAL("普通", 0),
    /** 提醒 */
    REMIND("提醒", 1),
    /** 重要 */
    IMPORTANT("重要", 2),
    /** 紧急 */
    URGENT("紧急", 3);

    private final String label;
    private final int value;

    NotificationPriorityEnum(String label, int value) {
        this.label = label;
        this.value = value;
    }

    public String label() {
        return label;
    }

    public int value() {
        return value;
    }

    public static boolean isValid(int value) {
        for (NotificationPriorityEnum e : values()) {
            if (e.value == value) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按值解析，非法值抛出 IllegalArgumentException（硬失败，错误信息列明完整值域）。
     */
    public static NotificationPriorityEnum of(int value) {
        for (NotificationPriorityEnum e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        throw new IllegalArgumentException("通知重要级别取值非法：" + value + "，合法值为 " + valueRange());
    }

    public static Optional<NotificationPriorityEnum> tryOf(int value) {
        for (NotificationPriorityEnum e : values()) {
            if (e.value == value) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }

    /** 合法值域描述，用于错误信息与文档 */
    public static String valueRange() {
        StringBuilder sb = new StringBuilder();
        for (NotificationPriorityEnum e : values()) {
            if (sb.length() > 0) {
                sb.append("/");
            }
            sb.append(e.value).append("(").append(e.label).append(")");
        }
        return sb.toString();
    }
}
