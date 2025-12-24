package cn.geelato.web.platform.event;

import cn.geelato.web.common.event.BusinessEvent;
import cn.geelato.web.platform.sse.SseHelper;
import cn.geelato.web.platform.sse.SseMessage;

import java.util.HashMap;
import java.util.Map;

public class UpgradePageEvent extends BusinessEvent {
    private final String pageId;

    public UpgradePageEvent(Object source, String pageId) {
        super(source);
        this.pageId = pageId;
    }

    @Override
    public String getEventCode() {
        return "UpgradePageEvent";
    }

    @Override
    public void handle() {
        Map<String, Object> data = new HashMap<>();
        data.put("DATA", getEventCode());
        data.put("PAGE_ID", pageId);
        SseHelper.push(new SseMessage("upgrade_page_topic", data));
    }
}