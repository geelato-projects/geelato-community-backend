package cn.geelato.web.common.controller.extractor;

import cn.geelato.web.common.controller.PageParams;
import java.util.Map;

/**
 * 通用请求参数提取器接口
 * 用于从不同类型的请求中提取各种参数
 * @param <T> 请求类型（如HttpServletRequest或ServerRequest）
 */
public interface RequestParamsExtractor<T> {
    
    /**
     * 从请求中提取分页参数
     * @param request 请求对象
     * @return 分页参数
     */
    PageParams extractPageParams(T request);
    
    /**
     * 从请求中提取分页参数，如果提取失败则使用默认值
     * @param request 请求对象
     * @param defaultPageNum 默认页码
     * @param defaultPageSize 默认页面大小
     * @return 分页参数
     */
    default PageParams extractPageParams(T request, int defaultPageNum, int defaultPageSize) {
        PageParams params = extractPageParams(request);
        if (params == null || !params.isValid()) {
            return new PageParams(defaultPageNum, defaultPageSize);
        }
        return params;
    }
    
    /**
     * 从请求中提取查询参数
     * @param request 请求对象
     * @return 查询参数Map
     */
    Map<String, String> extractQueryParams(T request);
    
    /**
     * 从请求中提取指定的查询参数
     * @param request 请求对象
     * @param paramName 参数名
     * @return 参数值
     */
    String extractQueryParam(T request, String paramName);
    
    /**
     * 从请求中提取Header参数
     * @param request 请求对象
     * @return Header参数Map
     */
    Map<String, String> extractHeaders(T request);
    
    /**
     * 从请求中提取指定的Header参数
     * @param request 请求对象
     * @param headerName Header名称
     * @return Header值
     */
    String extractHeader(T request, String headerName);
    
    /**
     * 从请求体中提取参数
     * @param request 请求对象
     * @return 请求体参数Map
     */
    Map<String, Object> extractBodyParams(T request);
    
    /**
     * 从请求体中提取指定的参数
     * @param request 请求对象
     * @param paramName 参数名
     * @return 参数值
     */
    Object extractBodyParam(T request, String paramName);
}