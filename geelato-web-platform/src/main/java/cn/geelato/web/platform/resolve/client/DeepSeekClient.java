package cn.geelato.web.platform.resolve.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class DeepSeekClient {
    private static final int CONNECT_TIMEOUT = 30000;
    private static final int READ_TIMEOUT = 180000;

    @Value("${geelato.ai.deepseek.base-url:https://api.deepseek.com/chat/completions}")
    private String deepseekBaseUrl;

    @Value("${geelato.ai.deepseek.api-key:}")
    private String deepseekApiKey;

    @Value("${geelato.ai.deepseek.model:deepseek-v4-pro}")
    private String deepseekModel;

    @Value("${geelato.ai.deepseek.thinking-enabled:true}")
    private boolean deepseekThinkingEnabled;

    @Value("${geelato.ai.deepseek.reasoning-effort:max}")
    private String deepseekReasoningEffort;

    public String extract(String userPrompt, String markdownContent) throws IOException {
        if (Strings.isBlank(markdownContent)) {
            throw new IllegalArgumentException("markdownContent is blank");
        }
        if (Strings.isBlank(userPrompt)) {
            userPrompt = "请按照我的描述提取需要的内容";
        }

        String systemPrompt = "你是一个文本提取工具，你需要按照我的描述，从markdown格式的原始内容文本内容，精准提取我需要的内容，原始内容是：\n\n"
                + markdownContent
                + "\n\n请严格按照以下要求返回结果：\n1. 仅返回 JSON 格式数据，不添加任何额外文字、注释、Markdown 代码块（```json/```）；\n2. JSON 内容需保证语法正确，可直接被程序解析；\n3. 返回内容示例：{\"订舱号\": \"xxx\", \"托运人\": \"xxx\"}";

        return chat(userPrompt, systemPrompt);
    }

    public String chat(String userPrompt, String systemPrompt) throws IOException {
        if (Strings.isBlank(deepseekApiKey)) {
            throw new IllegalArgumentException("deepseek api-key is not configured");
        }
        if (Strings.isBlank(userPrompt)) {
            throw new IllegalArgumentException("userPrompt is blank");
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", deepseekModel);
        requestBody.put("stream", false);
        if (deepseekThinkingEnabled) {
            Map<String, String> thinking = new HashMap<>();
            thinking.put("type", "enabled");
            requestBody.put("thinking", thinking);
            if (Strings.isNotBlank(deepseekReasoningEffort)) {
                requestBody.put("reasoning_effort", deepseekReasoningEffort);
            }
        }

        List<Map<String, String>> messages = new ArrayList<>();
        if (Strings.isNotBlank(systemPrompt)) {
            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messages.add(systemMessage);
        }

        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);
        messages.add(userMessage);
        requestBody.put("messages", messages);

        URL url = new URL(deepseekBaseUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setRequestProperty("Authorization", "Bearer " + deepseekApiKey);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = JSON.toJSONString(requestBody).getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("deepseek request failed with code: " + responseCode);
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
        }

        JSONObject jsonResponse = JSON.parseObject(response.toString());
        JSONObject error = jsonResponse.getJSONObject("error");
        if (error != null) {
            throw new IOException("deepseek api error: " + error.getString("message"));
        }

        JSONArray choices = jsonResponse.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return "";
        }

        JSONObject message = choices.getJSONObject(0).getJSONObject("message");
        return message == null ? "" : message.getString("content");
    }
}

