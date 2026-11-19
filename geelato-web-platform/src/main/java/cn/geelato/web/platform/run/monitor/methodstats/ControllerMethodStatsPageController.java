package cn.geelato.web.platform.run.monitor.methodstats;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Controller 方法调用统计 HTML 页面。通过显式 Controller 返回 static/method-stats.html，
 * 避免依赖 Spring Boot 默认静态资源机制（在某些宿主配置下可能被覆盖/禁用导致 404）。
 * 访问：GET /method-stats.html（普通 @RestController，不加 /api 前缀）。
 * 数据接口见 ControllerMethodStatsApiController（/api/method-stats/**）。
 */
@RestController
public class ControllerMethodStatsPageController {

    private static final String HTML_PATH = "static/method-stats.html";
    private volatile String cachedHtml;

    @GetMapping(value = {"/method-stats.html", "/method-stats"})
    public ResponseEntity<String> page() throws IOException {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(loadHtml());
    }

    private String loadHtml() throws IOException {
        if (cachedHtml != null) {
            return cachedHtml;
        }
        synchronized (this) {
            if (cachedHtml == null) {
                try (InputStream is = new ClassPathResource(HTML_PATH).getInputStream()) {
                    cachedHtml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            return cachedHtml;
        }
    }
}
