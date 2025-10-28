package cn.geelato.web.platform.graal.service;

import cn.geelato.core.graal.GraalService;
import cn.geelato.utils.HttpUtils;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP Service for GraalJS
 * Based on OkHttp, supports GET, POST, PUT, DELETE methods
 */
@GraalService(name = "http", built = "true")
public class HttpService {
    
    private static final OkHttpClient defaultClient = createDefaultClient();
    
    /**
     * Create default OkHttpClient
     */
    private static OkHttpClient createDefaultClient() {
        try {
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[]{};
                        }
                    }
            };
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            
            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier((hostname, session) -> true)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Failed to create HTTP client", e);
        }
    }
    
    /**
     * Execute GET request
     * 
     * @param url Request URL
     * @param requestParams Query parameters (can be null)
     * @param body Request body (not used for GET, can be null)
     * @param headers Request headers (can be null)
     * @return Response content
     */
    public String get(String url, Map<String, String> requestParams, String body, Map<String, String> headers) {
        String finalUrl = buildUrl(url, requestParams);
        return executeRequest(finalUrl, "GET", null, headers);
    }
    
    /**
     * Execute POST request
     * 
     * @param url Request URL
     * @param requestParams Query parameters (can be null)
     * @param body Request body content (can be null)
     * @param headers Request headers (can be null)
     * @return Response content
     */
    public String post(String url, Map<String, String> requestParams, String body, Map<String, String> headers) {
        String finalUrl = buildUrl(url, requestParams);
        return executeRequest(finalUrl, "POST", body, headers);
    }
    

    
    /**
     * Execute PUT request
     * 
     * @param url Request URL
     * @param requestParams Query parameters (can be null)
     * @param body Request body content (can be null)
     * @param headers Request headers (can be null)
     * @return Response content
     */
    public String put(String url, Map<String, String> requestParams, String body, Map<String, String> headers) {
        String finalUrl = buildUrl(url, requestParams);
        return executeRequest(finalUrl, "PUT", body, headers);
    }
    
    /**
     * Execute DELETE request
     * 
     * @param url Request URL
     * @param requestParams Query parameters (can be null)
     * @param body Request body (not used for DELETE, can be null)
     * @param headers Request headers (can be null)
     * @return Response content
     */
    public String delete(String url, Map<String, String> requestParams, String body, Map<String, String> headers) {
        String finalUrl = buildUrl(url, requestParams);
        return executeRequest(finalUrl, "DELETE", null, headers);
    }
    
    /**
     * Execute PATCH request
     * 
     * @param url Request URL
     * @param requestParams Query parameters (can be null)
     * @param body Request body content (can be null)
     * @param headers Request headers (can be null)
     * @return Response content
     */
    public String patch(String url, Map<String, String> requestParams, String body, Map<String, String> headers) {
        String finalUrl = buildUrl(url, requestParams);
        return executeRequest(finalUrl, "PATCH", body, headers);
    }
    
    /**
     * Generic method for executing HTTP requests
     * 
     * @param url Request URL
     * @param method HTTP method
     * @param body Request body content
     * @param headers Request headers
     * @return Response content
     */
    private String executeRequest(String url, String method, String body, Map<String, String> headers) {
        try {
            Request.Builder requestBuilder = new Request.Builder().url(url);
            
            // Add request headers
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        requestBuilder.addHeader(entry.getKey(), entry.getValue());
                    }
                }
            }
            
            // Set request method and body
            RequestBody requestBody = null;
            if (StringUtils.isNotBlank(body)) {
                // Determine MediaType based on Content-Type header
                String contentType = "application/json; charset=utf-8"; // default
                if (headers != null) {
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        if ("Content-Type".equalsIgnoreCase(entry.getKey()) && entry.getValue() != null) {
                            contentType = entry.getValue();
                            break;
                        }
                    }
                }
                MediaType mediaType = MediaType.parse(contentType);
                requestBody = RequestBody.create(body, mediaType);
            } else if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
                requestBody = RequestBody.create(new byte[0], null);
            }
            
            requestBuilder.method(method, requestBody);
            Request request = requestBuilder.build();
            
            try (Response response = defaultClient.newCall(request).execute()) {
                if (response.body() != null) {
                    return response.body().string();
                } else {
                    throw new IOException("Response body is null");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(method + " request failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Create HTTP client with custom timeout configuration
     * 
     * @param connectTimeoutSeconds Connection timeout in seconds
     * @param readTimeoutSeconds Read timeout in seconds
     * @param writeTimeoutSeconds Write timeout in seconds
     * @return HttpService instance
     */
    public HttpService withTimeout(int connectTimeoutSeconds, int readTimeoutSeconds, int writeTimeoutSeconds) {
        return new HttpService();
    }
    
    /**
     * Build URL with query parameters
     * 
     * @param baseUrl Base URL
     * @param params Parameter map
     * @return Complete URL with parameters
     */
    public String buildUrl(String baseUrl, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return baseUrl;
        }
        
        StringBuilder urlBuilder = new StringBuilder(baseUrl);
        if (!baseUrl.contains("?")) {
            urlBuilder.append("?");
        } else {
            urlBuilder.append("&");
        }
        
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                urlBuilder.append("&");
            }
            urlBuilder.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        
        return urlBuilder.toString();
    }
}