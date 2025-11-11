package cn.geelato.web.platform.event;

import cn.geelato.web.common.event.BusinessEvent;
import cn.geelato.web.platform.sse.SseHelper;
import cn.geelato.web.platform.sse.SseMessage;

import java.util.HashMap;
import java.util.Map;

public class UpgradeDictionaryEvent extends BusinessEvent  {
    public UpgradeDictionaryEvent(Object source) {
        super(source);
    }

    @Override
    public String getEventCode() {
        return "UpgradeDictionaryEvent";
    }

    @Override
    public void handle() {
        Map<String, Object> data = new HashMap<>();
        data.put("DATA", getEventCode());
        SseHelper.push(new SseMessage("upgrade_dictionary_topic",data));
    }
}
