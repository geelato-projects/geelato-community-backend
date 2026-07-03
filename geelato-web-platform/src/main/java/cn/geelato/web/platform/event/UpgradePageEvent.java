package cn.geelato.web.platform.event;

import cn.geelato.web.common.event.BusinessEvent;
import cn.geelato.web.platform.sse.SseHelper;
import cn.geelato.web.platform.sse.SseMessage;

import java.util.HashMap;
import java.util.Map;

public class UpgradePageEvent extends BusinessEvent {
    private final String pageId;
    private final String extendId;

    public UpgradePageEvent(Object source, String pageId,String extendId) {
        super(source);
        this.pageId = pageId;
        this.extendId = extendId;
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
        data.put("EXTEND_ID", extendId);
        SseHelper.push(new SseMessage("upgrade_page_topic", data));
    }
}