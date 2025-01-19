package cn.geelato.utils;

import okhttp3.*;

import java.io.IOException;
import java.util.Map;

public class HttpUtils {
    private static final OkHttpClient client = new OkHttpClient();

    public static String doGet(String url, Map<String, String> headers) throws IOException {
        Request.Builder requestBuilder = new Request.Builder()
                .url(url);
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                requestBuilder.addHeader(entry.getKey(), entry.getValue());
            }
        }
        Request request = requestBuilder.build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            } else {
                throw new IOException("Unexpected code " + response);
            }
        }
    }

    public static String doPost(String url, String json, Map<String, String> headers) throws IOException {
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(mediaType, json);
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .post(body);

        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                requestBuilder.addHeader(entry.getKey(), entry.getValue());
            }
        }
        Request request = requestBuilder.build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            } else {
                throw new IOException("Unexpected code " + response);
            }
        }
    }
}
