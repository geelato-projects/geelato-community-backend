package cn.geelato.metasync;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 实体三者同步工具 HTML 页面 Controller。
 * <p>
 * 通过显式 Controller 返回 static/meta-sync.html 的内容，
 * 避免依赖 Spring Boot 默认静态资源机制（在某些宿主配置下可能被覆盖/禁用导致 404）。
 * <p>
 * 访问：GET /meta-sync.html（不加 /api 前缀，因为用普通 @RestController）。
 */
@RestController
public class MetaSyncPageController {

    private static final String HTML_PATH = "static/meta-sync.html";
    private volatile String cachedHtml;

    @GetMapping(value = {"/meta-sync.html", "/meta-sync"})
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
