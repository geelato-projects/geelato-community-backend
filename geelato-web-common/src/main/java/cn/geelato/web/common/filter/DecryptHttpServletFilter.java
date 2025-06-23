package cn.geelato.web.common.filter;

import cn.geelato.utils.SM4Utils;
import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DecryptHttpServletFilter extends OncePerRequestFilter {
    private final String sm4Key = "b76278495b7f4df3";
    /**
     * 处理请求
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if ("POST".equalsIgnoreCase(request.getMethod()) &&
                "application/json".equalsIgnoreCase(request.getContentType())) {
            String requestBody = readRequestBody(request);
            try {
                JSONObject jsonObject = JSONObject.parseObject(requestBody);
                if (jsonObject.containsKey("edata")) {
                    String encryptedData = jsonObject.getString("edata");

                    if (encryptedData != null) {
                        String decryptedBody = SM4Utils.decrypt(encryptedData,sm4Key);
                        EncryptedRequestWrapper requestWrapper = new EncryptedRequestWrapper(request, decryptedBody);
                        filterChain.doFilter(requestWrapper, response);
                        return;
                    }
                }
            } catch (Exception e) {
            }
            EncryptedRequestWrapper requestWrapper = new EncryptedRequestWrapper(request, requestBody);
            filterChain.doFilter(requestWrapper, response);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    /**
     * 读取请求体内容
     */
    private String readRequestBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
