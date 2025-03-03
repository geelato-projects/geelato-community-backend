package cn.geelato.utils;

import okhttp3.*;

import java.io.IOException;
import java.util.Map;

public class HttpUtils {
    private static final OkHttpClient client = new OkHttpClient();

    /**
     * 通过HTTP GET请求获取指定URL的内容。
     *
     * @param url     请求的URL地址
     * @param headers 可选的HTTP请求头信息，以Map形式传递，键为头名称，值为头值
     * @return 返回从URL获取的内容，以字符串形式表示
     * @throws IOException 如果在请求过程中出现I/O错误，或者服务器返回非成功的HTTP状态码，则抛出此异常
     */
    public static String doGet(String url, Map<String, String> headers) throws IOException {
        Request.Builder requestBuilder = new Request.Builder().url(url);
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    requestBuilder.addHeader(entry.getKey(), entry.getValue());
                }
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

    /**
     * 通过HTTP POST请求向指定URL发送JSON数据，并返回服务器的响应内容。
     *
     * @param url     请求的URL地址
     * @param json    要发送的JSON数据
     * @param headers 可选的HTTP请求头信息，以Map形式传递，键为头名称，值为头值
     * @return 返回从服务器获取的内容，以字符串形式表示
     * @throws IOException 如果在请求过程中出现I/O错误，或者服务器返回非成功的HTTP状态码，则抛出此异常
     */
    public static String doPost(String url, String json, Map<String, String> headers) throws IOException {
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(mediaType, json);
        Request.Builder requestBuilder = new Request.Builder().url(url).post(body);
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    requestBuilder.addHeader(entry.getKey(), entry.getValue());
                }
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
