package cn.geelato.web.platform.srv.resolve;

import cn.geelato.web.platform.resolve.core.ResolveContext;
import cn.geelato.web.platform.resolve.integration.ResolvePipelineResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class ResolvePipelineRunner {
    private final ResolvePipelineResolver pipelineResolver;
    private final Map<String, MessageChannel> channels;

    public ResolvePipelineRunner(ResolvePipelineResolver pipelineResolver, Map<String, MessageChannel> channels) {
        this.pipelineResolver = pipelineResolver;
        this.channels = channels;
    }

    /**
     * 根据上下文路由到对应的集成管道，并等待管道处理完成后返回结果上下文。
     */
    public ResolveContext run(ResolveContext ctx) {
        String channelName = pipelineResolver.resolveChannelName(ctx);
        if (log.isDebugEnabled()) {
            log.debug("Running resolve pipeline: biztag={}, feature={}, sourceExt={}, channel={}",
                    ctx == null ? null : ctx.getBiztag(),
                    ctx == null ? null : ctx.getFeature(),
                    ctx == null ? null : ctx.getSourceExt(),
                    channelName);
        }
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
        ResolveContext result = (ResolveContext) reply.getPayload();
        if (log.isDebugEnabled()) {
            log.debug("Resolve pipeline finished: channel={}, steps={}, resultType={}",
                    channelName,
                    result == null || result.getSteps() == null ? 0 : result.getSteps().size(),
                    result == null || result.getResult() == null ? null : result.getResult().getClass().getSimpleName());
        }
        return result;
    }
}
