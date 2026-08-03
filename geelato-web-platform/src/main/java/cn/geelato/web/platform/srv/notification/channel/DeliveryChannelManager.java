package cn.geelato.web.platform.srv.notification.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 投递渠道注册中心：收集所有 {@link DeliveryChannel} Bean，按渠道标识索引。
 * outbox 调度器据此将待投递项路由到对应渠道实现。
 *
 * @author geelato
 */
@Component
@Slf4j
public class DeliveryChannelManager {

    private final Map<String, DeliveryChannel> channels = new HashMap<>();

    @Autowired(required = false)
    public void registerChannels(List<DeliveryChannel> channelList) {
        if (channelList == null) {
            return;
        }
        for (DeliveryChannel channel : channelList) {
            String key = channel.getChannel();
            DeliveryChannel existing = channels.put(key.toLowerCase(), channel);
            if (existing != null && existing != channel) {
                log.warn("通知投递渠道 {} 被覆盖：{} -> {}", key, existing.getClass().getName(), channel.getClass().getName());
            }
            log.info("注册通知投递渠道：{} -> {}", key, channel.getClass().getName());
        }
    }

    public DeliveryChannel getChannel(String channel) {
        if (channel == null) {
            return null;
        }
        return channels.get(channel.toLowerCase());
    }

    public boolean hasChannel(String channel) {
        return channel != null && channels.containsKey(channel.toLowerCase());
    }
}
