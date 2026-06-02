package cn.geelato.web.platform.resolve.ai;

import cn.geelato.web.platform.resolve.model.AiSuggestion;
import cn.geelato.web.platform.resolve.model.MappingCandidate;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class AiMatchClient {
    private final OkHttpClient httpClient;

    @Value("${geelato.resolve.ai.base-url:}")
    private String baseUrl;

    public AiMatchClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public AiSuggestion match(String biztag, String fieldKey, String rawText, List<MappingCandidate> candidates) {
        if (Strings.isBlank(baseUrl) || Strings.isBlank(rawText) || candidates == null || candidates.isEmpty()) {
            return null;
        }
        try {
            JSONObject bodyJson = new JSONObject();
            bodyJson.put("biztag", biztag);
            bodyJson.put("fieldKey", fieldKey);
            bodyJson.put("rawText", rawText);
            JSONArray candArr = new JSONArray();
            for (MappingCandidate c : candidates) {
                if (c == null) {
                    continue;
                }
                JSONObject o = new JSONObject();
                o.put("id", c.getId());
                o.put("code", c.getCode());
                o.put("name", c.getName());
                candArr.add(o);
            }
            bodyJson.put("candidates", candArr);

            RequestBody body = RequestBody.create(JSON.toJSONString(bodyJson), MediaType.parse("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(normalizeUrl(baseUrl) + "/match")
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return null;
                }
                String resp = response.body().string();
                JSONObject respJson = JSON.parseObject(resp);
                String selectedId = respJson.getString("selectedId");
                if (Strings.isBlank(selectedId)) {
                    return null;
                }
                AiSuggestion suggestion = new AiSuggestion();
                suggestion.setId(selectedId);
                suggestion.setConfidence(respJson.getDouble("confidence"));
                suggestion.setReason(respJson.getString("reason"));
                return suggestion;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeUrl(String url) {
        if (Strings.isBlank(url)) {
            return "";
        }
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}

