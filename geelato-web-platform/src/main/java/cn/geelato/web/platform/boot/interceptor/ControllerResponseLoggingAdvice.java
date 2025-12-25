package cn.geelato.web.platform.boot.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class ControllerResponseLoggingAdvice implements ResponseBodyAdvice<Object> {
    @Resource
    private ObjectMapper objectMapper;
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        if (request instanceof ServletServerHttpRequest) {
            HttpServletRequest req = ((ServletServerHttpRequest) request).getServletRequest();
            String json;
            try {
                json = objectMapper.writeValueAsString(body);
            } catch (Exception e) {
                json = String.valueOf(body);
            }
            if (json != null && json.length() > 10000) {
                json = json.substring(0, 10000);
            }
            req.setAttribute("ri.response", json);
        }
        return body;
    }
}

