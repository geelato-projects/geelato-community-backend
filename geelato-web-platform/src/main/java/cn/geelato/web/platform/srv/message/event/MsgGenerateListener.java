package cn.geelato.web.platform.srv.message.event;

import cn.geelato.message.model.PlatformMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitMessageOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Slf4j
public class MsgGenerateListener {

    @Resource
    private RabbitTemplate rabbitTemplate;
    @EventListener
    public void SendToMessageQueue(MsgGenerateEvent event) {
        try {
            PlatformMsg msg = event.getPlatformMsg();
            RabbitMessageOperations rabbitTemplate;

            log.info("消息已推送至RabbitMQ，消息ID: {}", msg.getId());
        } catch (Exception e) {
            log.error("推送消息到RabbitMQ失败，消息ID: {}", event.getPlatformMsg().getId(), e);
        }
    }
}
