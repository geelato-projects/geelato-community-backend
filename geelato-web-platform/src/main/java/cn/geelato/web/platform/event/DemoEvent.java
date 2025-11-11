package cn.geelato.web.platform.event;

import cn.geelato.web.common.event.BusinessEvent;

public class DemoEvent extends BusinessEvent {

    private final String message;
    public DemoEvent(Object source,String message) {
        super(source);
        this.message=message;
    }

    @Override
    public String getEventCode() {
        return "DemoEvent";
    }

    @Override
    public void handle() {
        System.out.println("Demo Event Handle:" + message);
    }
}
