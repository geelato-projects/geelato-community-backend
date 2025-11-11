package cn.geelato.web.platform.sse;

import cn.geelato.lang.api.ApiResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/subscribe")
public class SseController {
    @Autowired
    private SseEmitterManager sseManager;

    @GetMapping("/{topic}")
    public SseEmitter subscribe(@PathVariable String topic) {
        return SseHelper.subscribe(topic);
    }
    @GetMapping("/topic/all")
    public ApiResult<?> all() {
        return  null;
    }

}
