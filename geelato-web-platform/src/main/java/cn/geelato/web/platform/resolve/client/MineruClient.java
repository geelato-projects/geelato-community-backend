package cn.geelato.web.platform.resolve.client;

import com.alibaba.fastjson2.JSON;
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
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
@Slf4j
public class MineruClient {
    public static final String CONTENT_LIST_FILE = "content_list_v2.json";
    public static final String FULL_MD_FILE = "full.md";

    private static final int CONNECT_TIMEOUT = 30000;
    private static final int READ_TIMEOUT = 60000;

    @Value("${geelato.ocr.mineru.api-url:https://mineru.net/api/v4/extract/task}")
    private String mineruApiUrl;

    @Value("${geelato.ocr.mineru.authorization:}")
    private String mineruAuthorization;

    public String submitExtractionTask(String pdfUrl) throws IOException {
        if (Strings.isBlank(mineruAuthorization)) {
            throw new IllegalArgumentException("mineru authorization is not configured");
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("url", pdfUrl);
        requestBody.put("model_version", "vlm");
        requestBody.put("is_ocr", true);

        URL url = new URL(mineruApiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setRequestProperty("Authorization", mineruAuthorization);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = JSON.toJSONString(requestBody).getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("mineru request failed with code: " + responseCode);
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
        }

        JSONObject jsonResponse = JSON.parseObject(response.toString());
        if (jsonResponse.getIntValue("code") != 0) {
            throw new IOException("mineru api error: " + jsonResponse.getString("msg"));
        }

        String taskId = jsonResponse.getJSONObject("data").getString("task_id");
        if (Strings.isBlank(taskId)) {
            throw new IOException("mineru task id is empty");
        }
        return taskId;
    }

    public String pollTaskResult(String taskId) throws IOException, InterruptedException {
        if (Strings.isBlank(mineruAuthorization)) {
            throw new IllegalArgumentException("mineru authorization is not configured");
        }
        if (Strings.isBlank(taskId)) {
            throw new IllegalArgumentException("taskId is blank");
        }

        String pollUrl = mineruApiUrl + "/" + taskId;
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
                throw new IOException("mineru poll failed with code: " + responseCode);
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }

            JSONObject jsonResponse = JSON.parseObject(response.toString());
            if (jsonResponse.getIntValue("code") != 0) {
                throw new IOException("mineru poll api error: " + jsonResponse.getString("msg"));
            }

            JSONObject data = jsonResponse.getJSONObject("data");
            String state = data.getString("state");
            if ("done".equals(state)) {
                String fullZipUrl = data.getString("full_zip_url");
                if (Strings.isBlank(fullZipUrl)) {
                    throw new IOException("mineru full_zip_url is empty");
                }
                return fullZipUrl;
            } else if ("failed".equals(state)) {
                throw new IOException("mineru task failed: " + data.getString("err_msg"));
            }

            Thread.sleep(1000);
            retryCount++;
        }

        throw new IOException("mineru polling timeout after " + maxRetries + " seconds");
    }

    public String downloadAndExtractFile(String zipUrl, String fileName) throws IOException {
        if (Strings.isBlank(zipUrl)) {
            throw new IllegalArgumentException("zipUrl is blank");
        }
        if (Strings.isBlank(fileName)) {
            throw new IllegalArgumentException("fileName is blank");
        }

        URL url = new URL(zipUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("zip download failed with code: " + responseCode);
        }

        try (ZipInputStream zipIn = new ZipInputStream(connection.getInputStream())) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                if (fileName.equals(entry.getName())) {
                    byte[] content = zipIn.readAllBytes();
                    return new String(content, StandardCharsets.UTF_8);
                }
                zipIn.closeEntry();
            }
        }

        throw new IOException("file not found in zip: " + fileName);
    }
}

