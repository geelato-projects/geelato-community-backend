package cn.geelato.alert.engine.deliver;

import java.util.Map;

/**
 * 告警通道 → geelato-message 消息类型映射。alert 契约域（AlertRuleService.VALID_CHANNELS）
 * 与 message type 枚举不同名：wework → weixin_work_group；voice 预留无映射，硬失败。
 */
public final class ChannelRouter {

    private static final Map<String, String> TYPE_BY_CHANNEL = Map.of(
            "inapp", "inapp",
            "email", "email",
            "sms", "sms",
            "bot", "bot",
            "wework", "weixin_work_group");

    private ChannelRouter() {
    }

    public static String toMessageType(String channel) {
        String mapped = TYPE_BY_CHANNEL.get(channel);
        if (mapped == null) {
            throw new DeliverException("通道无消息中心映射: " + channel + "（voice 预留未接入，已映射: " + TYPE_BY_CHANNEL.keySet() + "）");
        }
        return mapped;
    }

    public static boolean supports(String channel) {
        return TYPE_BY_CHANNEL.containsKey(channel);
    }
}
