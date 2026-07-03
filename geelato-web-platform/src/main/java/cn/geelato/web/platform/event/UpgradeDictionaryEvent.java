package cn.geelato.web.platform.event;

import cn.geelato.web.common.event.BusinessEvent;
import cn.geelato.web.platform.sse.SseHelper;
import cn.geelato.web.platform.sse.SseMessage;

import java.util.HashMap;
import java.util.Map;

public class UpgradeDictionaryEvent extends BusinessEvent {
    private final String dictId;

    public UpgradeDictionaryEvent(Object source, String dictId) {
        super(source);
        this.dictId = dictId;
    }

    @Override
    public String getEventCode() {
        return "UpgradeDictionaryEvent";
    }

    @Override
    public void handle() {
        Map<String, Object> data = new HashMap<>();
        data.put("DATA", getEventCode());
        data.put("DICT_ID", dictId);
        SseHelper.push(new SseMessage("upgrade_dictionary_topic", data));
    }
}
