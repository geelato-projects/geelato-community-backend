package cn.geelato.alert.engine.deliver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * geelato-message HTTP 传输实现。自建 OkHttpClient（超时可配；信任所有证书——内网自签部署形态，
 * 对齐 HttpOrmHookActionExecutor 先例）；2xx 判定成功，失败抛 DeliverException 携响应摘要。
 */
public class OkHttpMessageTransport implements MessageTransport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int ERROR_SNIPPET_MAX = 200;

    private final OkHttpClient client;
    private final String baseUrl;
    private final String systemCode;
    private final String apiKey;

    public OkHttpMessageTransport(String baseUrl, long timeoutMs, String systemCode, String apiKey) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("geelato.alert.engine.message-base-url 未配置（引擎启用时必填）");
        }
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.systemCode = blankToNull(systemCode);
        this.apiKey = blankToNull(apiKey);
        this.client = cn.geelato.utils.HttpUtils.getUnsafeOkHttpClient().newBuilder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public List<String> enqueue(EnqueuePayload payload) {
        String body = payload.toJson();
        Request.Builder rb = new Request.Builder()
                .url(baseUrl + "/message/enqueue")
                .post(RequestBody.create(okhttp3.MediaType.parse("application/json; charset=utf-8"), body));
        String resp = execute(rb, "enqueue " + payload.getBizKey());
        try {
            JsonNode ids = MAPPER.readTree(resp).path("data");
            if (!ids.isArray() || ids.isEmpty()) {
                throw new DeliverException("enqueue 响应缺少 data.id 数组: " + snippet(resp));
            }
            List<String> out = new ArrayList<>();
            for (JsonNode id : ids) {
                out.add(id.asText());
            }
            return out;
        } catch (DeliverException e) {
            throw e;
        } catch (Exception e) {
            throw new DeliverException("enqueue 响应解析失败: " + snippet(resp), e);
        }
    }

    @Override
    public List<Map<String, Object>> listByBizKey(String bizKey) {
        Request.Builder rb = new Request.Builder()
                .url(baseUrl + "/message/ops/messages?bizKey=" + urlEncode(bizKey) + "&size=200")
                .get();
        String resp = execute(rb, "listByBizKey " + bizKey);
        try {
            JsonNode data = MAPPER.readTree(resp).path("data");
            List<Map<String, Object>> out = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode row : data) {
                    Map<String, Object> m = MAPPER.convertValue(row, Map.class);
                    // 行级校验：message 侧未支持 bizKey 过滤时参数被忽略返回无关行，显式失败而非静默混入
                    if (!bizKey.equals(String.valueOf(m.get("bizKey")))) {
                        throw new DeliverException("message 回查返回了 bizKey 不匹配的行（message 侧需支持 bizKey 过滤）: "
                                + snippet(String.valueOf(m.get("bizKey"))));
                    }
                    out.add(m);
                }
            }
            return out;
        } catch (DeliverException e) {
            throw e;
        } catch (Exception e) {
            throw new DeliverException("listByBizKey 响应解析失败: " + snippet(resp), e);
        }
    }

    private String execute(Request.Builder rb, String action) {
        rb.header("Content-Type", "application/json");
        if (systemCode != null && apiKey != null) {
            rb.header("X-System-Code", systemCode);
            rb.header("Authorization", "Bearer " + apiKey);
        }
        try (Response response = client.newCall(rb.build()).execute()) {
            String respBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new DeliverException(action + " 失败 HTTP " + response.code() + ": " + snippet(respBody));
            }
            return respBody;
        } catch (DeliverException e) {
            throw e;
        } catch (IOException e) {
            throw new DeliverException(action + " 网络异常: " + e.getMessage(), e);
        }
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String snippet(String text) {
        if (text == null) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() <= ERROR_SNIPPET_MAX ? compact : compact.substring(0, ERROR_SNIPPET_MAX) + "...";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
