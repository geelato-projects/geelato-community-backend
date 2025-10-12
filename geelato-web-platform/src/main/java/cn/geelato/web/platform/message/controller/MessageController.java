package cn.geelato.web.platform.message.controller;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.message.entity.PlatformMsg;
import cn.geelato.web.platform.message.entity.UniMsg;
import cn.geelato.web.platform.message.mapper.PlatformMsgMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@ApiRestController("/message/uni")
@Slf4j
public class MessageController extends BaseController {

    @Autowired
    private PlatformMsgMapper platformMsgMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @RequestMapping(value = "/send", method = RequestMethod.POST)
    public ApiResult<?> send(@RequestBody UniMsg msg) {
        try {
            // 创建PlatformMsg实体
            PlatformMsg platformMsg = new PlatformMsg();
            platformMsg.setTitle(msg.getTitle());
            platformMsg.setContent(msg.getContent());
            platformMsg.setSender(msg.getSender());
            platformMsg.setType(msg.getType());
            platformMsg.setBuss(msg.getBuss());
            platformMsg.setStatus("draft");
            platformMsg.setChannel("default");
            // 将MsgReceiver序列化为JSON
            if (msg.getReceiver() != null) {
                String receiverJson = objectMapper.writeValueAsString(msg.getReceiver());
                platformMsg.setReceiver(receiverJson);
            }
            
            // 插入数据库
            int result = platformMsgMapper.insert(platformMsg);
            
            if (result > 0) {
                log.info("消息插入成功，ID: {}", platformMsg.getId());
                return ApiResult.success(platformMsg.getId());
            } else {
                log.error("消息插入失败");
                return ApiResult.fail("消息插入失败");
            }
            
        } catch (Exception e) {
            log.error("发送消息时发生异常", e);
            return ApiResult.fail("发送消息失败: " + e.getMessage());
        }
    }
}
