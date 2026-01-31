package cn.geelato.web.platform.ai;

import cn.geelato.web.common.annotation.ApiRestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@ApiRestController("/ai")
public class AiController {
    @Autowired
    private AiService aiService;

    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@RequestBody Map<String, String> body) {
        String content = body != null ? body.get("content") : null;
        return aiService.ask(content);
    }
}
