package cn.geelato.web.platform.srv.settings.service;

import cn.geelato.web.platform.srv.base.service.BaseService;
import cn.geelato.meta.Message;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

/**
 * @author diabl
 * 消息服务类
 */
@Component
public class MessageService extends BaseService {

    public void updateStatus(Message message, int status) {
        if (message != null && Strings.isNotBlank(message.getId())) {
            message.setSendStatus(status);
            updateModel(message);
        }
    }

    public void updateReceiver(Message message, String receiver) {
        if (message != null && Strings.isNotBlank(message.getId())) {
            message.setReceiver(receiver);
            updateModel(message);
        }
    }

    public void updateContent(Message message, String title, String content, int status) {
        if (message != null && Strings.isNotBlank(message.getId())) {
            message.setTitle(title);
            message.setContent(content);
            message.setSendStatus(status);
            updateModel(message);
        }
    }
}
