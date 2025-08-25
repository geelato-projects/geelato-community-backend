package cn.geelato.web.common.controller.extractor;

import cn.geelato.utils.StringUtils;
import cn.geelato.web.common.controller.PageParams;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Enumeration;

/**
 * Servlet环境下的通用请求参数提取器
 * 支持从URL查询参数、请求体和Header中提取各种参数
 */
public class ServletRequestParamsExtractor implements RequestParamsExtractor<HttpServletRequest> {
    

    
    @Override
    public PageParams extractPageParams(HttpServletRequest request) {
        // 首先尝试从URL查询参数中获取
        PageParams params = extractPageParamsFromQuery(request);
        
        // 如果查询参数中没有有效的分页参数，尝试从请求体中获取
        if (!params.isValid()) {
            params = extractPageParamsFromBody(request);
        }
        
        return params;
    }
    
    @Override
    public Map<String, String> extractQueryParams(HttpServletRequest request) {
        Map<String, String> queryParams = new HashMap<>();
        Enumeration<String> paramNames = request.getParameterNames();
        
        while (paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement();
            String paramValue = request.getParameter(paramName);
            queryParams.put(paramName, paramValue);
        }
        
        return queryParams;
    }
    
    @Override
    public String extractQueryParam(HttpServletRequest request, String paramName) {
        return request.getParameter(paramName);
    }
    
    @Override
    public Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            headers.put(headerName, headerValue);
        }
        
        return headers;
    }
    
    @Override
    public String extractHeader(HttpServletRequest request, String headerName) {
        return request.getHeader(headerName);
    }
    
    @Override
    public Map<String, Object> extractBodyParams(HttpServletRequest request) {
        try {
            String requestBody = getRequestBody(request);
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
    public Object extractBodyParam(HttpServletRequest request, String paramName) {
        Map<String, Object> bodyParams = extractBodyParams(request);
        return bodyParams.get(paramName);
    }
    
    private PageParams extractPageParamsFromQuery(HttpServletRequest request) {
        String pageNumStr = request.getParameter("pageNum");
        String pageSizeStr = request.getParameter("pageSize");
        
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
    
    private PageParams extractPageParamsFromBody(HttpServletRequest request) {
        try {
            String requestBody = getRequestBody(request);
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
    
    private String getRequestBody(HttpServletRequest request) {
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = request.getReader();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (IOException e) {
            return null;
        }
    }
}