package cn.geelato.script.kit.builtin;

import cn.geelato.core.graal.GraalFunction;
import cn.geelato.core.graal.GraalService;
import cn.geelato.script.kit.KitContext;
import com.alibaba.fastjson2.JSON;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 出网 kit（$gl.http）：JDK HttpClient，便捷方法返回响应体字符串、request 返回完整响应。
 * 三重管控：http.enabled 总开关、allowlist 主机白名单（精确 host / "*." 前缀域名，条目可带 scheme 限定）、
 * 单请求超时不超过 http.timeout-ms。不做证书绕过（自签场景由宿主 JVM 信任库解决）。
 */
@GraalService(name = "http", built = "true", descrption = "HTTP 出网（get/post/put/delete/patch/request；enabled+allowlist+timeout 管控）")
public class HttpKit {

    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

    private final KitContext ctx;

    public HttpKit(KitContext ctx) {
        this.ctx = ctx;
    }

    @GraalFunction(example = "$gl.http.get(url, {page: 1}, {'X-Trace': 't1'})", description = "GET，返回响应体字符串")
    public String get(String url, Map<String, Object> params, Map<String, Object> headers) {
        return body(execute(Map.of("method", "GET", "url", url, "params", orEmpty(params), "headers", orEmpty(headers))));
    }

    @GraalFunction(example = "$gl.http.post(url, {name: 'a'}, {})", description = "POST（body 为字符串原样、对象 JSON 序列化），返回响应体字符串")
    public String post(String url, Object body, Map<String, Object> headers) {
        return body(execute(Map.of("method", "POST", "url", url, "body", body == null ? "" : body, "headers", orEmpty(headers))));
    }

    @GraalFunction(example = "$gl.http.put(url, {name: 'a'}, {})", description = "PUT，返回响应体字符串")
    public String put(String url, Object body, Map<String, Object> headers) {
        return body(execute(Map.of("method", "PUT", "url", url, "body", body == null ? "" : body, "headers", orEmpty(headers))));
    }

    @GraalFunction(example = "$gl.http.patch(url, {name: 'a'}, {})", description = "PATCH，返回响应体字符串")
    public String patch(String url, Object body, Map<String, Object> headers) {
        return body(execute(Map.of("method", "PATCH", "url", url, "body", body == null ? "" : body, "headers", orEmpty(headers))));
    }

    @GraalFunction(example = "$gl.http.delete(url, {})", description = "DELETE，返回响应体字符串")
    public String delete(String url, Map<String, Object> headers) {
        return body(execute(Map.of("method", "DELETE", "url", url, "headers", orEmpty(headers))));
    }

    @GraalFunction(example = "$gl.http.request({method: 'POST', url: url, params: {}, body: {}, headers: {}, timeoutMs: 3000})",
            description = "完整请求，返回 {status, headers, body}（headers 为 名→[值] Map）")
    public Object request(Map<String, Object> req) {
        return cn.geelato.script.lang.GuestValues.toGuest(execute(req));
    }

    private Map<String, Object> execute(Map<String, Object> req) {
        if (!ctx.isHttpEnabled()) {
            throw new IllegalArgumentException("HTTP 出网未启用（geelato.script.engine.http.enabled）");
        }
        String url = str(req.get("url"));
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("HTTP 请求缺少 url");
        }
        URI uri = resolveAndCheck(url);
        String method = upper(req.get("method"));
        if (method == null || method.isBlank()) {
            method = "GET";
        }
        Object params = req.get("params");
        if (params instanceof Map<?, ?> m && !m.isEmpty()) {
            uri = URI.create(uri + (uri.getRawQuery() == null ? "?" : "&") + encodeQuery(m));
        }
        long timeoutMs = ctx.getHttpTimeoutMs();
        if (req.get("timeoutMs") instanceof Number n && n.longValue() > 0) {
            timeoutMs = Math.min(n.longValue(), ctx.getHttpTimeoutMs());
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(Math.max(1, timeoutMs)));
        applyHeaders(builder, req.get("headers"));
        String publishBody = null;
        if (!"GET".equals(method) && !"DELETE".equals(method)) {
            Object body = req.get("body");
            publishBody = body instanceof CharSequence ? body.toString() : JSON.toJSONString(body);
            builder.header("Content-Type", JSON_CONTENT_TYPE);
        }
        builder.method(method, publishBody == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(publishBody, StandardCharsets.UTF_8));
        long start = System.currentTimeMillis();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1, timeoutMs)))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            ctx.record(cn.geelato.script.core.TraceEntry.of("http", method,
                    response.statusCode() + " " + uri, 0, System.currentTimeMillis() - start));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", response.statusCode());
            Map<String, Object> outHeaders = new LinkedHashMap<>();
            response.headers().map().forEach((k, v) -> outHeaders.put(k, v));
            out.put("headers", outHeaders);
            out.put("body", response.body());
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("HTTP 请求失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP 请求被中断", e);
        }
    }

    private static String body(Map<String, Object> response) {
        return str(response.get("body"));
    }

    private static void applyHeaders(HttpRequest.Builder builder, Object headers) {
        if (headers instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    builder.header(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                }
            }
        }
    }

    /** allowlist 校验并解析 URI；条目支持精确 host、"*." 前缀域名、可选 "scheme://" 限定。 */
    private URI resolveAndCheck(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("HTTP url 非法: " + url);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("HTTP url 缺少 host: " + url);
        }
        List<String> allowlist = ctx.getHttpAllowlist();
        if (allowlist == null || allowlist.isEmpty()) {
            throw new IllegalArgumentException("HTTP 出网白名单未配置（geelato.script.engine.http.allowlist），拒绝访问 " + host);
        }
        for (String rule : allowlist) {
            if (rule == null || rule.isBlank()) {
                continue;
            }
            String scheme = null;
            String pattern = rule.trim();
            int idx = pattern.indexOf("://");
            if (idx >= 0) {
                scheme = pattern.substring(0, idx);
                pattern = pattern.substring(idx + 3);
            }
            boolean hostMatch = pattern.startsWith("*.")
                    ? host.endsWith(pattern.substring(1))
                    : host.equalsIgnoreCase(pattern);
            if (hostMatch && (scheme == null || scheme.equalsIgnoreCase(uri.getScheme()))) {
                return uri;
            }
        }
        throw new IllegalArgumentException("HTTP 目标不在白名单内: " + host + "（allowlist: " + allowlist + "）");
    }

    private static String encodeQuery(Map<?, ?> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<?, ?> e : params.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(String.valueOf(e.getKey()), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static Map<String, Object> orEmpty(Map<String, Object> map) {
        return map == null ? Map.of() : map;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String upper(Object o) {
        return o == null ? null : String.valueOf(o).toUpperCase();
    }
}
