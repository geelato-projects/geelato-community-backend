package cn.geelato.web.platform.srv.resolve;

import cn.geelato.web.platform.resolve.core.ResolveContext;
import cn.geelato.web.platform.resolve.integration.ResolvePipelineResolver;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ResolvePipelineRunner {
    private final ResolvePipelineResolver pipelineResolver;
    private final Map<String, MessageChannel> channels;

    public ResolvePipelineRunner(ResolvePipelineResolver pipelineResolver, Map<String, MessageChannel> channels) {
        this.pipelineResolver = pipelineResolver;
        this.channels = channels;
    }

    public ResolveContext run(ResolveContext ctx) {
        String channelName = pipelineResolver.resolveChannelName(ctx);
        MessageChannel channel = channels.get(channelName);
        if (channel == null) {
            throw new IllegalStateException("resolve pipeline not found: " + channelName);
        }
        MessagingTemplate template = new MessagingTemplate();
        template.setReceiveTimeout(180000L);
        Message<?> reply = template.sendAndReceive(channel, MessageBuilder.withPayload(ctx).build());
        if (reply == null) {
            throw new IllegalStateException("resolve pipeline timeout");
        }
        return (ResolveContext) reply.getPayload();
    }
}

