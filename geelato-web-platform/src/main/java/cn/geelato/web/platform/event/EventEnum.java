package cn.geelato.web.platform.event;

import cn.geelato.web.common.event.EventEnumInterface;

public enum EventEnum implements EventEnumInterface<Object> {

    TEST_EVENT{
        @Override
        public String getEventCode() {
            return "TEST_EVENT";
        }

        @Override
        public void handle(Object data) {
            System.out.println("TEST_EVENT");
        }
    },

}
