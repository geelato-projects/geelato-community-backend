package cn.geelato.web.common.controller;

import cn.geelato.core.SessionCtx;
import cn.geelato.web.common.controller.extractor.RequestParamsExtractor;
import cn.geelato.web.common.controller.extractor.ServletRequestParamsExtractor;
import cn.geelato.web.common.controller.extractor.ReactiveRequestParamsExtractor;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Setter;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 通用控制器基类
 * 支持Servlet和Reactive两种环境下的分页参数处理
 */
public class BaseController {
    protected HttpServletResponse response;
    protected HttpServletRequest request;
    
    // ==================== ParameterOperator相关常量和字段 ====================
    
    public static final String OPERATOR_SEPARATOR = "|";

    @Setter
    protected ServerRequest serverRequest;
    
    // ==================== 参数提取相关配置和组件 ====================
    
    @Value("${geelato.web.architecture:servlet}")
    private String webArchitecture;
    
    private final ServletRequestParamsExtractor servletExtractor = new ServletRequestParamsExtractor();
    private ReactiveRequestParamsExtractor reactiveExtractor;
    
    /**
     * 获取通用参数提取器
     * 根据配置选择Servlet或Reactive实现
     */
    private RequestParamsExtractor getExtractor() {
        if ("reactor".equalsIgnoreCase(webArchitecture)) {
            if (reactiveExtractor == null) {
                reactiveExtractor = new ReactiveRequestParamsExtractor();
            }
            return reactiveExtractor;
        }
        return servletExtractor;
    }


    /**
     * 通过Spring框架的@ModelAttribute注解自动注入HttpServletResponse对象
     * 这个方法主要用于设置HttpServletResponse对象，以便在类内部使用
     *
     * @param response Servlet响应对象，用于获取响应相关的信息
     */
    @ModelAttribute
    public void setReqAndRes(HttpServletRequest request, HttpServletResponse response) {
        this.request=request;
        this.response = response;
    }

    // ==================== 通用参数提取方法 ====================
    
    /**
     * 获取查询参数
     * @param paramName 参数名
     * @return 参数值
     */
    protected String getQueryParam(String paramName) {
        RequestParamsExtractor extractor = getExtractor();
        if (this.request != null) {
            return extractor.extractQueryParam(this.request, paramName);
        }
        if (this.serverRequest != null) {
            return extractor.extractQueryParam(this.serverRequest, paramName);
        }
        return null;
    }
    
    /**
     * 获取所有查询参数
     * @return 查询参数Map
     */
    protected Map<String, String> getQueryParams() {
        RequestParamsExtractor extractor = getExtractor();
        if (this.request != null) {
            return extractor.extractQueryParams(this.request);
        }
        if (this.serverRequest != null) {
            return extractor.extractQueryParams(this.serverRequest);
        }
        return new HashMap<>();
    }
    
    /**
     * 获取Header参数
     * @param headerName Header名称
     * @return Header值
     */
    protected String getHeader(String headerName) {
        RequestParamsExtractor extractor = getExtractor();
        if (this.request != null) {
            return extractor.extractHeader(this.request, headerName);
        }
        if (this.serverRequest != null) {
            return extractor.extractHeader(this.serverRequest, headerName);
        }
        return null;
    }
    
    /**
     * 获取所有Header参数
     * @return Header参数Map
     */
    protected Map<String, String> getHeaders() {
        RequestParamsExtractor extractor = getExtractor();
        if (this.request != null) {
            return extractor.extractHeaders(this.request);
        }
        if (this.serverRequest != null) {
            return extractor.extractHeaders(this.serverRequest);
        }
        return new HashMap<>();
    }
    
    /**
     * 获取请求体参数
     * @param paramName 参数名
     * @return 参数值
     */
    protected Object getBodyParam(String paramName) {
        RequestParamsExtractor extractor = getExtractor();
        if (this.request != null) {
            return extractor.extractBodyParam(this.request, paramName);
        }
        if (this.serverRequest != null) {
            return extractor.extractBodyParam(this.serverRequest, paramName);
        }
        return null;
    }
    
    /**
     * 获取所有请求体参数
     * @return 请求体参数Map
     */
    protected Map<String, Object> getBodyParams() {
        RequestParamsExtractor extractor = getExtractor();
        if (this.request != null) {
            return extractor.extractBodyParams(this.request);
        }
        if (this.serverRequest != null) {
            return extractor.extractBodyParams(this.serverRequest);
        }
        return new HashMap<>();
    }
    
    /**
     * 获取请求中的应用ID（兼容Servlet和Reactive环境）
     */
    public String getAppId() {
        return getHeader("App-Id");
    }

    /**
     * 获取请求中的或上下文中的租户代码（兼容Servlet和Reactive环境）
     */
    public String getTenantCode() {
        String tenantCode = getHeader("Tenant-Code");
        // 如果都没有获取到，则从上下文获取
        return Strings.isNotBlank(tenantCode) ? tenantCode : SessionCtx.getCurrentTenantCode();
    }
    
    // ==================== 统一分页方法 ====================
    
    /**
     * 获取分页参数（自动适配当前环境）
     * 支持从URL查询参数和请求体中提取
     * 
     * @return 分页参数
     */
    protected PageParams getPageParams() {
        RequestParamsExtractor extractor = getExtractor();
        if (this.request != null) {
            return extractor.extractPageParams(this.request);
        }
        if (this.serverRequest != null) {
            return extractor.extractPageParams(this.serverRequest);
        }
        return new PageParams(1, 10); // 默认值
    }
    
    /**
     * 获取分页参数，如果无效则使用默认值（自动适配当前环境）
     * 
     * @param defaultPageNum 默认页码
     * @param defaultPageSize 默认页面大小
     * @return 分页参数
     */
    protected PageParams getPageParams(int defaultPageNum, int defaultPageSize) {
        RequestParamsExtractor extractor = getExtractor();
        if (this.request != null) {
            return extractor.extractPageParams(this.request, defaultPageNum, defaultPageSize);
        }
        if (this.serverRequest != null) {
            return extractor.extractPageParams(this.serverRequest, defaultPageNum, defaultPageSize);
        }
        return new PageParams(defaultPageNum, defaultPageSize);
    }
    
    /**
     * 创建MyBatis Plus的Page对象（自动适配当前环境）
     * 
     * @param <T> 实体类型
     * @return Page对象
     */
    protected <T> Page<T> createPage() {
        PageParams params = getPageParams();
        return new Page<>(params.getCurrent(), params.getSize());
    }
    
    /**
     * 创建MyBatis Plus的Page对象，带默认值（自动适配当前环境）
     * 
     * @param defaultPageNum 默认页码
     * @param defaultPageSize 默认页面大小
     * @param <T> 实体类型
     * @return Page对象
     */
    protected <T> Page<T> createPage(int defaultPageNum, int defaultPageSize) {
        PageParams params = getPageParams(defaultPageNum, defaultPageSize);
        return new Page<>(params.getCurrent(), params.getSize());
    }
    

    
    
    // ==================== 通用工具方法 ====================
    
    /**
     * 直接从分页参数创建Page对象
     * 
     * @param pageParams 分页参数
     * @param <T> 实体类型
     * @return Page对象
     */
    public <T> Page<T> createPage(PageParams pageParams) {
        return new Page<>(pageParams.getCurrent(), pageParams.getSize());
    }
    
    /**
     * 直接从分页参数创建Page对象，带默认值
     * 
     * @param pageParams 分页参数
     * @param defaultPageNum 默认页码
     * @param defaultPageSize 默认页面大小
     * @param <T> 实体类型
     * @return Page对象
     */
    public <T> Page<T> createPage(PageParams pageParams, int defaultPageNum, int defaultPageSize) {
        return new Page<>(pageParams.getCurrent(), pageParams.getSize());
    }
    

    
    // ==================== ParameterOperator方法重写 ====================
    
    /**
     * 获取查询参数（支持架构判断）
     */
    protected Map<String, Object> getQueryParameters(Class elementType) {
        return getQueryParameters(elementType, false);
    }

    protected Map<String, Object> getQueryParameters(Class elementType, boolean isOperation) {
        if ("reactor".equalsIgnoreCase(webArchitecture) && serverRequest != null) {
            return getQueryParametersFromReactor(elementType, isOperation);
        } else {
            return getQueryParameters(elementType, this.request, isOperation);
        }
    }
    
    /**
     * 获取查询参数（三参数版本，用于兼容现有代码）
     */
    protected Map<String, Object> getQueryParameters(Class elementType, Map<String, Object> requestBodyMap, boolean isOperation) {
        // 合并请求参数和请求体参数
        Map<String, Object> queryParams = getQueryParameters(elementType, isOperation);
        if (requestBodyMap != null) {
            queryParams.putAll(requestBodyMap);
        }
        return queryParams;
    }

    @Deprecated
    protected Map<String, Object> getQueryParameters(Class elementType, HttpServletRequest request, boolean isOperation) {
        Map<String, Object> queryParamsMap = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            Set<String> fieldNames = getClassFieldNames(elementType);
            String key = getParameterMapKey(entry.getKey(), isOperation);
            if (fieldNames.contains(key)) {
                List<String> values = Arrays.asList(entry.getValue());
                if (values.size() == 1) {
                    queryParamsMap.put(entry.getKey(), values.get(0));
                } else {
                    queryParamsMap.put(entry.getKey(), values.toArray(new String[0]));
                }
            }
        }
        return queryParamsMap;
    }

    protected Map<String, Object> getQueryParameters() {
        if ("reactor".equalsIgnoreCase(webArchitecture) && serverRequest != null) {
            return getQueryParametersFromReactor();
        } else {
            return getQueryParameters(this.request);
        }
    }

    @Deprecated
    protected Map<String, Object> getQueryParameters(HttpServletRequest request) {
        Map<String, Object> queryParamsMap = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            List<String> values = Arrays.asList(entry.getValue());
            if (values.size() == 1) {
                queryParamsMap.put(entry.getKey(), values.get(0));
            } else {
                queryParamsMap.put(entry.getKey(), values.toArray(new String[0]));
            }
        }
        return queryParamsMap;
    }

    protected Map<String, Object> getRequestBody() {
        if ("reactor".equalsIgnoreCase(webArchitecture) && serverRequest != null) {
            return getRequestBodyFromReactor();
        } else {
            Map<String, Object> requestBodyMap;
            try {
                String requestBody = resolveGql(this.request);
                requestBodyMap = JSON.parseObject(requestBody, Map.class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return requestBodyMap;
        }
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 从Reactor ServerRequest中提取查询参数
     */
    private Map<String, Object> getQueryParametersFromReactor(Class elementType, boolean isOperation) {
        Map<String, Object> queryParamsMap = new LinkedHashMap<>();
        if (serverRequest != null) {
            serverRequest.queryParams().forEach((key, values) -> {
                Set<String> fieldNames = getClassFieldNames(elementType);
                String paramKey = getParameterMapKey(key, isOperation);
                if (fieldNames.contains(paramKey)) {
                    if (values.size() == 1) {
                        queryParamsMap.put(key, values.get(0));
                    } else {
                        queryParamsMap.put(key, values.toArray(new String[0]));
                    }
                }
            });
        }
        return queryParamsMap;
    }

    private Map<String, Object> getQueryParametersFromReactor() {
        Map<String, Object> queryParamsMap = new LinkedHashMap<>();
        if (serverRequest != null) {
            serverRequest.queryParams().forEach((key, values) -> {
                if (values.size() == 1) {
                    queryParamsMap.put(key, values.get(0));
                } else {
                    queryParamsMap.put(key, values.toArray(new String[0]));
                }
            });
        }
        return queryParamsMap;
    }

    private Map<String, Object> getRequestBodyFromReactor() {
        try {
            return new LinkedHashMap<>();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Set<String> getClassFieldNames(Class elementType) {
        Set<String> fieldNameList = new HashSet<>();
        List<Field> fieldsList = getClassFields(elementType);
        for (Field field : fieldsList) {
            fieldNameList.add(field.getName());
        }
        return fieldNameList;
    }

    private List<Field> getClassFields(Class elementType) {
        List<Field> fieldsList = new ArrayList<>();
        while (elementType != null) {
            Field[] declaredFields = elementType.getDeclaredFields();
            fieldsList.addAll(Arrays.asList(declaredFields));
            elementType = elementType.getSuperclass();
        }
        return fieldsList;
    }

    private String getParameterMapKey(String key, boolean isOperation) {
        if (isOperation && Strings.isNotBlank(key) && key.contains(OPERATOR_SEPARATOR)) {
            return key.substring(0, key.lastIndexOf(OPERATOR_SEPARATOR));
        }
        return key;
    }

    public String resolveGql(HttpServletRequest request) {
        StringBuilder stringBuilder = new StringBuilder();
        BufferedReader br = null;
        String str;
        try {
            br = request.getReader();
            if (br != null) {
                while ((str = br.readLine()) != null) {
                    stringBuilder.append(str);
                }
            }
        } catch (IOException ignored) {
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return stringBuilder.toString();
    }

    /**
     * 格式化查询参数的键。
     *
     * @param key 查询参数的键
     * @return 格式化后的查询参数键
     */
    private Map<String, String> formatMapKey(String key) {
        Map<String, String> map = new HashMap<>();
        map.put("key", key);
        if (Strings.isNotBlank(key) && key.contains(OPERATOR_SEPARATOR)) {
            int index = key.lastIndexOf(OPERATOR_SEPARATOR);
            map.put("key", key.substring(0, index));
            if (key.length() > index + 1) {
                map.put("operator", key.substring(index + 1));
            }
        }
        return map;
    }
}