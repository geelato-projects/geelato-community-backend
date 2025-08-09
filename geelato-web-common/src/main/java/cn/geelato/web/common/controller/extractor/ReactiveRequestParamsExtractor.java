package cn.geelato.web.common.controller.extractor;

import cn.geelato.utils.StringUtils;
import cn.geelato.web.common.controller.PageParams;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Reactive环境下的通用请求参数提取器
 * 支持从URL查询参数、请求体和Header中提取各种参数
 */
public class ReactiveRequestParamsExtractor implements RequestParamsExtractor<ServerRequest> {
    

    
    @Override
    public PageParams extractPageParams(ServerRequest request) {
        // 首先尝试从URL查询参数中获取
        PageParams queryParams = extractPageParamsFromQuery(request);
        
        // 如果查询参数中有有效的分页参数，直接返回
        if (queryParams != null && queryParams.isValid()) {
            return queryParams;
        }
        
        // 否则尝试从请求体中获取（同步方式）
        try {
            PageParams bodyParams = extractPageParamsFromBodySync(request);
            if (bodyParams != null && bodyParams.isValid()) {
                return bodyParams;
            }
        } catch (Exception e) {
            // 忽略异常，返回查询参数
        }
        
        return queryParams;
    }
    
    @Override
    public Map<String, String> extractQueryParams(ServerRequest request) {
        Map<String, String> queryParams = new HashMap<>();
        request.queryParams().forEach((key, values) -> {
            if (!values.isEmpty()) {
                queryParams.put(key, values.get(0));
            }
        });
        return queryParams;
    }
    
    @Override
    public String extractQueryParam(ServerRequest request, String paramName) {
        return request.queryParam(paramName).orElse(null);
    }
    
    @Override
    public Map<String, String> extractHeaders(ServerRequest request) {
        Map<String, String> headers = new HashMap<>();
        request.headers().asHttpHeaders().forEach((key, values) -> {
            if (!values.isEmpty()) {
                headers.put(key, values.get(0));
            }
        });
        return headers;
    }
    
    @Override
    public String extractHeader(ServerRequest request, String headerName) {
        return request.headers().firstHeader(headerName);
    }
    
    @Override
    public Map<String, Object> extractBodyParams(ServerRequest request) {
        try {
            // 尝试同步获取请求体内容
            String requestBody = request.bodyToMono(String.class).block();
            
            if (requestBody == null || requestBody.trim().isEmpty()) {
                return new HashMap<>();
            }
            
            // 解析JSON
            JSONObject jsonObject = JSON.parseObject(requestBody);
            Map<String, Object> bodyParams = new HashMap<>();
            
            for (String key : jsonObject.keySet()) {
                Object value = jsonObject.get(key);
                bodyParams.put(key, value);
            }
            
            return bodyParams;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
    
    @Override
    public Object extractBodyParam(ServerRequest request, String paramName) {
        Map<String, Object> bodyParams = extractBodyParams(request);
        return bodyParams.get(paramName);
    }
    
    private PageParams extractPageParamsFromQuery(ServerRequest request) {
        String pageNumStr = request.queryParam("pageNum").orElse(null);
        String pageSizeStr = request.queryParam("pageSize").orElse(null);
        
        Integer pageNum = null;
        Integer pageSize = null;
        
        if (StringUtils.isNotBlank(pageNumStr)) {
            try {
                pageNum = Integer.parseInt(pageNumStr);
            } catch (NumberFormatException e) {
                // 忽略解析错误
            }
        }
        
        if (StringUtils.isNotBlank(pageSizeStr)) {
            try {
                pageSize = Integer.parseInt(pageSizeStr);
            } catch (NumberFormatException e) {
                // 忽略解析错误
            }
        }
        
        return new PageParams(pageNum, pageSize);
    }
    
    private PageParams extractPageParamsFromBodySync(ServerRequest request) {
        try {
            String requestBody = request.bodyToMono(String.class).block();
            if (StringUtils.isBlank(requestBody)) {
                return null;
            }
            
            // 解析JSON
            JSONObject jsonObject = JSON.parseObject(requestBody);
            
            Integer pageNum = null;
            Integer pageSize = null;
            
            if (jsonObject.containsKey("pageNum") && jsonObject.get("pageNum") != null) {
                pageNum = jsonObject.getInteger("pageNum");
            }
            
            if (jsonObject.containsKey("pageSize") && jsonObject.get("pageSize") != null) {
                pageSize = jsonObject.getInteger("pageSize");
            }
            
            return new PageParams(pageNum, pageSize);
            
        } catch (Exception e) {
            return null;
        }
    }
}