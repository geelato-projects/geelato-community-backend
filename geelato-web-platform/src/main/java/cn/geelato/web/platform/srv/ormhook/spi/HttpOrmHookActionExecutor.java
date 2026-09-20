package cn.geelato.web.platform.srv.ormhook.spi;

import cn.geelato.meta.OrmHook;
import cn.geelato.utils.HttpUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * http 动作执行器：按钩子配置直接调用 HTTP 接口。
 * <p>
 * 配置来源 {@code platform_orm_hook} 的 http_method/http_url/http_headers/http_body：
 * <ul>
 *   <li>方法：GET | POST | PUT | DELETE | PATCH（默认 POST）</li>
 *   <li>请求头：JSON 对象，如 {@code {"Authorization":"Bearer x"}}；有请求体时默认补
 *       {@code Content-Type: application/json}（显式配置优先）</li>
 *   <li>请求体：模板，支持 {@code ${路径}} 占位符按点号路径取触发载荷
 *       （如 {@code ${values.id}}、{@code ${session.userId}}、{@code ${entityName}}）；
 *       无法解析的占位符原样保留；留空默认发送完整载荷 JSON</li>
 * </ul>
 * 判定：HTTP 2xx 为成功；非 2xx、IO 异常为失败（进重试/死信）。
 * 客户端复用 {@link HttpUtils} 的 OkHttp 实例（默认超时：连接/读/写各 10s，与平台
 * GraalJS http 服务同源）。内网定位；调用外部地址的 SSRF 面与既有脚本 http 服务一致。
 *
 * @author geelato
 */
@Component
@Slf4j
public class HttpOrmHookActionExecutor implements OrmHookActionExecutor {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");
    private static final String DEFAULT_METHOD = "POST";

    @Override
    public String getType() {
        return TYPE_HTTP;
    }

    @Override
    public OrmHookActionResult execute(OrmHook rule, Map<String, Object> payload) {
        String url = renderPlaceholders(rule.getHttpUrl(), payload);
        String method = rule.getHttpMethod() == null || rule.getHttpMethod().isBlank()
                ? DEFAULT_METHOD : rule.getHttpMethod().trim().toUpperCase();
        try {
            Request.Builder builder = new Request.Builder().url(url);
            boolean hasContentType = false;
            String headersJson = rule.getHttpHeaders();
            if (headersJson != null && !headersJson.isBlank()) {
                JSONObject headers = JSON.parseObject(headersJson);
                for (Map.Entry<String, Object> entry : headers.entrySet()) {
                    String name = entry.getKey();
                    String value = String.valueOf(entry.getValue());
                    builder.header(name, value);
                    hasContentType = hasContentType || name.equalsIgnoreCase("Content-Type");
                }
            }
            RequestBody requestBody = null;
            if (!"GET".equals(method)) {
                String body = rule.getHttpBody();
                String content = (body == null || body.isBlank())
                        ? JSON.toJSONString(payload) : renderPlaceholders(body, payload);
                requestBody = RequestBody.create(content, JSON_MEDIA_TYPE);
            }
            if (requestBody != null && !hasContentType) {
                builder.header("Content-Type", "application/json");
            }
            switch (method) {
                case "GET" -> builder.get();
                case "PUT" -> builder.put(Objects.requireNonNull(requestBody));
                case "DELETE" -> builder.delete(requestBody);
                case "PATCH" -> builder.patch(Objects.requireNonNull(requestBody));
                default -> builder.post(Objects.requireNonNull(requestBody));
            }

            OkHttpClient client = HttpUtils.getUnsafeOkHttpClient();
            try (Response response = client.newCall(builder.build()).execute()) {
                int code = response.code();
                String respBody = response.body() != null ? response.body().string() : "";
                if (code >= 200 && code < 300) {
                    if (log.isDebugEnabled()) {
                        log.debug("ORM Hook http 动作执行完成 hookId={}, {} {} -> {} {}", rule.getId(), method, url, code, abbreviate(respBody));
                    }
                    return OrmHookActionResult.success();
                }
                String err = String.format("HTTP %d: %s", code, abbreviate(respBody));
                log.warn("ORM Hook http 动作失败 hookId={}, {} {} -> {}", rule.getId(), method, url, err);
                return OrmHookActionResult.fail(err);
            }
        } catch (Exception e) {
            log.warn("ORM Hook http 动作执行异常 hookId={}, {} {}: {}", rule.getId(), method, url, e.getMessage());
            return OrmHookActionResult.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** ${路径} 占位符渲染：按点号路径从载荷取值；无法解析的占位符原样保留。 */
    private String renderPlaceholders(String template, Map<String, Object> payload) {
        if (template == null || template.indexOf("${") < 0) {
            return template;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            Object value = resolvePath(payload, matcher.group(1).trim());
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value == null ? matcher.group(0) : String.valueOf(value)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 点号路径取值：values.id、session.userId 等；中途非 Map 或不存在返回 null。 */
    private Object resolvePath(Map<String, Object> payload, String path) {
        if (path.isEmpty()) {
            return null;
        }
        Object current = payload;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(segment)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    private String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
