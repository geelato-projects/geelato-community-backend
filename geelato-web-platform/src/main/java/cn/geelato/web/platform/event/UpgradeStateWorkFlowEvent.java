package cn.geelato.web.platform.event;

import cn.geelato.web.common.event.BusinessEvent;
import cn.geelato.web.platform.sse.SseHelper;
import cn.geelato.web.platform.sse.SseMessage;

import java.util.HashMap;
import java.util.Map;

public class UpgradeStateWorkFlowEvent extends BusinessEvent {
    private final String procDefId;

    public UpgradeStateWorkFlowEvent(Object source, String procDefId) { 
        super(source);
        this.procDefId = procDefId;
    }

    @Override
    public String getEventCode() {
        return "UpgradeStateWorkFlowEvent";
    }

    @Override
    public void handle() {
        Map<String, Object> data = new HashMap<>();
        data.put("DATA", getEventCode());
        data.put("PROC_DEF_ID", procDefId);
        SseHelper.push(new SseMessage("upgrade_state_workflow_topic", data));
    }
}