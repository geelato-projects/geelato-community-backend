package cn.geelato.web.platform.srv.ocr;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@ApiRestController(value = "/ocr/advance")
@Slf4j
public class AdvanceOCRController extends BaseController {

    private static final String CONTENT_LIST_FILE = "content_list_v2.json";
    private static final String FULL_MD_FILE = "full.md";
    private static final int CONNECT_TIMEOUT = 30000;
    private static final int READ_TIMEOUT = 60000;

    @Value("${geelato.ocr.mineru.api-url:https://mineru.net/api/v4/extract/task}")
    private String mineruApiUrl;

    @Value("${geelato.ocr.mineru.authorization:}")
    private String mineruAuthorization;

    @Value("${geelato.ai.deepseek.base-url:https://api.deepseek.com/v1/chat/completions}")
    private String deepseekBaseUrl;

    @Value("${geelato.ai.deepseek.api-key:}")
    private String deepseekApiKey;

    @Value("${geelato.ai.deepseek.model:deepseek-chat}")
    private String deepseekModel;

    @Value("${geelato.ocr.pdf.base-url:http://47.121.135.61}")
    private String pdfBaseUrl;

    @Value("${geelato.ocr.pdf.save-path:/ocrpdf}")
    private String pdfSavePath;

    @RequestMapping(value = "/resolve", method = RequestMethod.POST)
    public ApiResult<?> resolve(@RequestParam("file") MultipartFile file,
                                @RequestParam(value = "config", required = false) String config) {
        log.info("=== OCR Advance Resolve Request Started ===");
        log.info("Received file: {}, size: {} bytes", file.getOriginalFilename(), file.getSize());
        log.info("Config: {}", config);

        try {
            if (file.isEmpty()) {
                log.error("File is null or empty");
                return ApiResult.fail("File is required");
            }

            AdvanceResolveRequest resolveRequest = parseConfig(config);

            String fileId = saveFile(file);
            log.info("File saved with id: {}", fileId);

            String pdfUrl = constructPdfUrl(fileId);
            log.info("Constructed PDF URL: {}", pdfUrl);

            String taskId = submitExtractionTask(pdfUrl);

            String fullZipUrl = pollTaskResult(taskId);

            if (ExtractTypeEnum.AI.getValue().equals(resolveRequest.getExtractType())) {
                log.info("Using AI extraction mode");
                return extractWithAI(fullZipUrl, resolveRequest.getPrompt());
            } else {
                log.info("Using Natural extraction mode");
                String jsonContent = downloadAndExtractFile(fullZipUrl, CONTENT_LIST_FILE);
                log.info("Successfully extracted content from zip");
                return ApiResult.success(JSON.parse(jsonContent));
            }
        } catch (Exception e) {
            log.error("OCR advance resolve failed: {}", e.getMessage(), e);
            return ApiResult.fail("OCR resolve failed: " + e.getMessage());
        } finally {
            log.info("=== OCR Advance Resolve Request Finished ===");
        }
    }

    private AdvanceResolveRequest parseConfig(String config) {
        log.debug("Parsing config: {}", config);
        AdvanceResolveRequest request = new AdvanceResolveRequest();
        if (config != null && !config.isEmpty()) {
            try {
                JSONObject jsonObject = JSON.parseObject(config);
                request.setExtractType(jsonObject.getString("extractType"));
                request.setPrompt(jsonObject.getString("prompt"));
                log.debug("Config parsed - extractType: {}, prompt: {}", request.getExtractType(), request.getPrompt());
            } catch (Exception e) {
                log.warn("Failed to parse config, using defaults: {}", e.getMessage());
            }
        }
        if (request.getExtractType() == null || request.getExtractType().isEmpty()) {
            request.setExtractType(ExtractTypeEnum.NATURAL.getValue());
            log.debug("Extract type is empty, using default: NATURAL");
        }
        log.info("Final resolve request - extractType: {}, hasPrompt: {}", request.getExtractType(), request.getPrompt() != null && !request.getPrompt().isEmpty());
        return request;
    }

    private ApiResult<?> extractWithAI(String zipUrl, String prompt) throws Exception {
        log.info("Starting AI extraction mode");

        String fullMdContent = downloadAndExtractFile(zipUrl, FULL_MD_FILE);
        log.info("Successfully extracted full.md from zip, content length: {}", fullMdContent.length());

        if (prompt == null || prompt.isEmpty()) {
            prompt = "请按照我的描述提取需要的内容";
            log.debug("Prompt is empty, using default");
        }

        String systemPrompt = "你是一个文本提取工具，你需要按照我的描述，从markdown格式的原始内容文本内容，精准提取我需要的内容，原始内容是：\n\n" + fullMdContent + "\n\n请严格按照以下要求返回结果：\n1. 仅返回 JSON 格式数据，不添加任何额外文字、注释、Markdown 代码块（```json/```）；\n2. JSON 内容需保证语法正确，可直接被程序解析；\n3. 返回内容示例：{\"订舱号\": \"xxx\", \"托运人\": \"xxx\"}";

        log.debug("System prompt length: {}, user prompt: {}", systemPrompt.length(), prompt);

        String aiResponse = callDeepSeekAI(prompt, systemPrompt);
        log.info("AI extraction completed, response length: {}", aiResponse.length());

        return ApiResult.success(aiResponse);
    }

    private String callDeepSeekAI(String userPrompt, String systemPrompt) throws Exception {
        log.info("Calling DeepSeek AI for content extraction");

        if (deepseekApiKey == null || deepseekApiKey.isEmpty()) {
            log.error("DeepSeek api-key is not configured");
            throw new IllegalArgumentException("deepseek api-key is not configured");
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", deepseekModel);
        requestBody.put("stream", false);

        List<Map<String, String>> messages = new ArrayList<>();

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
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

        log.debug("DeepSeek request - baseUrl: {}, model: {}", deepseekBaseUrl, deepseekModel);

        URL url = new URL(deepseekBaseUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setRequestProperty("Authorization", "Bearer " + deepseekApiKey);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = JSON.toJSONString(requestBody).getBytes("UTF-8");
            os.write(input, 0, input.length);
            log.debug("DeepSeek request sent, body size: {} bytes", input.length);
        }

        int responseCode = connection.getResponseCode();
        log.info("DeepSeek API response code: {}", responseCode);

        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("DeepSeek API request failed with code: " + responseCode);
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
        }

        JSONObject jsonResponse = JSON.parseObject(response.toString());
        log.debug("DeepSeek API response: {}", jsonResponse);

        JSONObject error = jsonResponse.getJSONObject("error");
        if (error != null) {
            String errorMsg = error.getString("message");
            log.error("DeepSeek API error: {}", errorMsg);
            throw new IOException("DeepSeek API error: " + errorMsg);
        }

        JSONArray choices = jsonResponse.getJSONArray("choices");
        String aiResponse = "";
        if (choices != null && !choices.isEmpty()) {
            JSONObject message = choices.getJSONObject(0).getJSONObject("message");
            aiResponse = message.getString("content");
            log.info("DeepSeek AI response received, content length: {}", aiResponse.length());
        } else {
            log.warn("DeepSeek API returned empty choices");
        }

        return aiResponse;
    }

    private String saveFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            originalFilename = "upload_" + System.currentTimeMillis() + ".pdf";
            log.warn("Original filename is empty, using default: {}", originalFilename);
        }

        String fileName = UUID.randomUUID().toString().replace("-", "") + "_" + originalFilename;
        File targetDir = new File(pdfSavePath);
        if (!targetDir.exists()) {
            boolean created = targetDir.mkdirs();
            log.info("Creating save directory: {}, result: {}", targetDir.getAbsolutePath(), created);
        }

        File targetFile = new File(targetDir, fileName);
        file.transferTo(targetFile);
        log.info("File saved successfully, path: {}, size: {} bytes", targetFile.getAbsolutePath(), file.getSize());

        return fileName;
    }

    private String constructPdfUrl(String fileName) {
        return pdfBaseUrl + "/" + fileName;
    }

    private String submitExtractionTask(String pdfUrl) throws IOException {
        log.info("Submitting extraction task for PDF: {}", pdfUrl);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("url", pdfUrl);
        requestBody.put("model_version", "vlm");
        requestBody.put("is_ocr", true);

        if (mineruAuthorization == null || mineruAuthorization.isEmpty()) {
            log.error("Minuer authorization is not configured");
            throw new IllegalArgumentException("mineru authorization is not configured");
        }

        URL url = new URL(mineruApiUrl);
        log.debug("Minuer API URL: {}", mineruApiUrl);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setRequestProperty("Authorization", mineruAuthorization);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = JSON.toJSONString(requestBody).getBytes("UTF-8");
            os.write(input, 0, input.length);
            log.debug("Request body sent, size: {} bytes", input.length);
        }

        int responseCode = connection.getResponseCode();
        log.info("Minuer API response code: {}", responseCode);

        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP request failed with code: " + responseCode);
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
        }

        JSONObject jsonResponse = JSON.parseObject(response.toString());
        log.debug("Minuer API response: {}", jsonResponse);

        if (jsonResponse.getIntValue("code") != 0) {
            log.error("Minuer API error, code: {}, msg: {}", jsonResponse.getIntValue("code"), jsonResponse.getString("msg"));
            throw new IOException("API error: " + jsonResponse.getString("msg"));
        }

        String taskId = jsonResponse.getJSONObject("data").getString("task_id");
        log.info("Extraction task submitted successfully, taskId: {}", taskId);

        return taskId;
    }

    private String pollTaskResult(String taskId) throws IOException, InterruptedException {
        log.info("Starting to poll task result, taskId: {}", taskId);

        if (mineruAuthorization == null || mineruAuthorization.isEmpty()) {
            log.error("Mineru authorization is not configured");
            throw new IllegalArgumentException("mineru authorization is not configured");
        }

        String pollUrl = mineruApiUrl + "/" + taskId;
        log.debug("Polling URL: {}", pollUrl);

        int maxRetries = 120;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            URL url = new URL(pollUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("Authorization", mineruAuthorization);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                log.error("Poll request failed with HTTP code: {}", responseCode);
                throw new IOException("HTTP request failed with code: " + responseCode);
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }

            JSONObject jsonResponse = JSON.parseObject(response.toString());

            if (jsonResponse.getIntValue("code") != 0) {
                log.error("Poll API error, code: {}, msg: {}", jsonResponse.getIntValue("code"), jsonResponse.getString("msg"));
                throw new IOException("API error: " + jsonResponse.getString("msg"));
            }

            JSONObject data = jsonResponse.getJSONObject("data");
            String state = data.getString("state");
            log.debug("Task {} poll response, state: {}, retryCount: {}/{}", taskId, state, retryCount, maxRetries);

            if ("done".equals(state)) {
                String fullZipUrl = data.getString("full_zip_url");
                log.info("Task {} completed successfully, zip URL: {}", taskId, fullZipUrl);
                return fullZipUrl;
            } else if ("failed".equals(state)) {
                String errMsg = data.getString("err_msg");
                log.error("Task {} failed, error: {}", taskId, errMsg);
                throw new IOException("Task failed: " + errMsg);
            }

            log.info("Task {} state: {}, waiting {}/{} seconds...", taskId, state, retryCount, maxRetries);
            Thread.sleep(1000);
            retryCount++;
        }

        log.error("Task {} polling timeout after {} retries", taskId, maxRetries);
        throw new IOException("Task polling timeout after " + maxRetries + " seconds");
    }

    private String downloadAndExtractFile(String zipUrl, String fileName) throws IOException {
        log.info("Downloading and extracting file from zip, zipUrl: {}, targetFile: {}", zipUrl, fileName);

        URL url = new URL(zipUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);

        int responseCode = connection.getResponseCode();
        log.debug("Zip download response code: {}", responseCode);

        if (responseCode != HttpURLConnection.HTTP_OK) {
            log.error("Failed to download zip, HTTP code: {}", responseCode);
            throw new IOException("Failed to download zip, HTTP code: " + responseCode);
        }

        try (ZipInputStream zipIn = new ZipInputStream(connection.getInputStream())) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                log.debug("Reading zip entry: {}, size: {}", entry.getName(), entry.getSize());
                if (fileName.equals(entry.getName())) {
                    byte[] content = zipIn.readAllBytes();
                    log.info("File extracted successfully, name: {}, content length: {}", fileName, content.length);
                    return new String(content, StandardCharsets.UTF_8);
                }
                zipIn.closeEntry();
            }
        }

        log.error("File not found in zip: {}", fileName);
        throw new IOException("File not found in zip: " + fileName);
    }

    @Getter
    public enum ExtractTypeEnum {
        NATURAL("natural", "自然提取"),
        AI("ai", "AI提取");

        private final String value;
        private final String label;

        ExtractTypeEnum(String value, String label) {
            this.value = value;
            this.label = label;
        }

    }

    @Data
    public static class AdvanceResolveRequest {
        private String extractType;
        private String prompt;
    }
}