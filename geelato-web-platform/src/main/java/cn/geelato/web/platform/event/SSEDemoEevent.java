package cn.geelato.web.platform.event;

import cn.geelato.web.common.event.BusinessEvent;
import cn.geelato.web.platform.sse.SseHelper;
import cn.geelato.web.platform.sse.SseMessage;

import java.util.HashMap;
import java.util.Map;

public class SSEDemoEevent extends BusinessEvent {
    public SSEDemoEevent(Object source) {
        super(source);
    }

    @Override
    public String getEventCode() {
        return "SSEDemoEevent";
    }

    @Override
    public void handle() {
        Map<String, Object> data = new HashMap<>();
        data.put("DATA", getEventCode());
        SseHelper.push(new SseMessage("demo_topic",data));
    }
}
