package cn.geelato.web.common.controller.extractor;

import cn.geelato.web.common.controller.PageParams;

/**
 * 分页参数提取器接口
 * 用于从不同类型的请求中提取分页参数
 * @param <T> 请求类型（如HttpServletRequest或ServerRequest）
 */
public interface PageParamsExtractor<T> {
    
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
}