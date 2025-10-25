package cn.geelato.web.platform.sse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/sse")
public class NoticeSubscribeController {

    @Autowired
    private SSEManager sseManager;

    @GetMapping("/subscribe/{userId}")
    public SseEmitter subscribeNotice(@PathVariable String userId) {
        return sseManager.createConnection(userId);
    }

    @GetMapping("/send/{userId}")
    public String sendNotice(@PathVariable String userId, @RequestParam String message) {
        sseManager.sendMessage(userId, message);
        return "消息已发送";
    }
}
