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

        // 只处理POST请求且Content-Type为application/json
        if ("POST".equalsIgnoreCase(request.getMethod()) &&
                "application/json".equalsIgnoreCase(request.getContentType())) {

            // 读取请求体
            String requestBody = readRequestBody(request);

            try {
                // 尝试解析JSON
                JSONObject jsonObject = JSONObject.parseObject(requestBody);

                // 检查是否包含edata字段
                if (jsonObject.containsKey("edata")) {
                    String encryptedData = jsonObject.getString("edata");

                    if (encryptedData != null) {
                        // 解密请求体
                        String decryptedBody = SM4Utils.decrypt(encryptedData,sm4Key);

                        // 将解密后的内容作为新的请求体
                        EncryptedRequestWrapper requestWrapper = new EncryptedRequestWrapper(request, decryptedBody);

                        // 继续处理请求
                        filterChain.doFilter(requestWrapper, response);
                        return;
                    }
                }
            } catch (Exception e) {
                // 解析失败或解密失败，继续处理原始请求体
            }

            // 默认情况：使用原始请求体
            EncryptedRequestWrapper requestWrapper = new EncryptedRequestWrapper(request, requestBody);
            filterChain.doFilter(requestWrapper, response);
        } else {
            // 非POST请求或非JSON请求直接放行
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
