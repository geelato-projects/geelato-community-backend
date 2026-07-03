package cn.geelato.web.platform.sse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/subscribe")
public class SseController {
    @Autowired
    private SseEmitterManager sseManager;

    @GetMapping(value = "/{topic}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable String topic) {
        if (topic == null || topic.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "topic不能为空");
        }
        return SseHelper.subscribe(topic);
    }

    @GetMapping(value = "/topic/all", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter all() {
        return SseHelper.subscribeAll();
    }
}
