package cn.geelato.web.platform.example;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.common.event.EventPublisher;
import cn.geelato.web.platform.event.DemoEvent;
import cn.geelato.web.platform.event.SSEDemoEevent;
import cn.geelato.web.platform.event.UpgradeDictionaryEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;


@ApiRestController("/example")
@Slf4j
public class AllExampleController {
    @RequestMapping(value = "/send_event", method = RequestMethod.GET)
    public ApiResult<?> send_event() {
        EventPublisher.publish(new DemoEvent(this,"ok"));
        EventPublisher.publish(new SSEDemoEevent(this));
        EventPublisher.publish(new UpgradeDictionaryEvent(this));
        return ApiResult.success("success");
    }
}
