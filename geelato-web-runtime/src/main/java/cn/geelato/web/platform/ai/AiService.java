package cn.geelato.web.platform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Service
public class AiService {
    private final ObjectMapper objectMapper;
    private final Executor executor;
    private final OkHttpClient httpClient;

    @Value("${geelato.ai.deepseek.base-url:https://api.deepseek.com/v1/chat/completions}")
    private String baseUrl;

    @Value("${geelato.ai.deepseek.api-key:sk-edd3a443d89a450ab521087bf170795e}")
    private String apiKey;

    @Value("${geelato.ai.deepseek.model:deepseek-chat}")
    private String model;

    @Value("${geelato.ai.deepseek.system-prompt:你是一个有帮助的助手}")
    private String systemPrompt;

    public AiService(ObjectMapper objectMapper, @Qualifier("eventExecutor") Executor executor) {
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
    }

    public SseEmitter ask(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("content不能为空");
        }
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        executor.execute(() -> streamDeepSeek(content, emitter));
        return emitter;
    }

    private void streamDeepSeek(String content, SseEmitter emitter) {
        try {
            Request request = buildRequest(content);
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    emitter.completeWithError(new RuntimeException("DeepSeek请求失败: " + response.code() + " " + errorBody));
                    return;
                }
                if (response.body() == null) {
                    emitter.completeWithError(new RuntimeException("DeepSeek响应为空"));
                    return;
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isEmpty()) {
                            continue;
                        }
                        if (line.startsWith("data:")) {
                            String payload = line.substring(5).trim();
                            if ("[DONE]".equals(payload)) {
                                emitter.complete();
                                return;
                            }
                            String chunk = extractChunk(payload);
                            if (chunk != null && !chunk.isEmpty()) {
                                emitter.send(SseEmitter.event().data(chunk).reconnectTime(3000));
                            } else {
                                emitter.send(SseEmitter.event().data(payload).reconnectTime(3000));
                            }
                        }
                    }
                }
                emitter.complete();
            }
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
    }

    private Request buildRequest(String content) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("stream", true);
        ArrayNode messages = root.putArray("messages");
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            ObjectNode system = objectMapper.createObjectNode();
            system.put("role", "system");
            system.put("content", systemPrompt);
            messages.add(system);
        }
        ObjectNode user = objectMapper.createObjectNode();
        user.put("role", "user");
        user.put("content", buildUserPrompt(content));
        messages.add(user);
        RequestBody body = RequestBody.create(objectMapper.writeValueAsString(root), MediaType.parse("application/json; charset=utf-8"));
        Request.Builder builder = new Request.Builder()
                .url(baseUrl)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "text/event-stream");
        if (apiKey != null && !apiKey.isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + apiKey);
        }
        return builder.build();
    }

    private String extractChunk(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode delta = choices.get(0).path("delta");
                if (delta.hasNonNull("content")) {
                    return delta.get("content").asText();
                }
                JsonNode message = choices.get(0).path("message");
                if (message.hasNonNull("content")) {
                    return message.get("content").asText();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String buildUserPrompt(String content) {
        return "请作为AI助手回答：这个问题如何处理？问题内容：" + content;
    }
}
