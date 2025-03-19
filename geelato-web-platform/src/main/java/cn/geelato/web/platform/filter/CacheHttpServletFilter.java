package cn.geelato.web.platform.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

@SuppressWarnings("ALL")
public class CacheHttpServletFilter implements Filter {
    private final String __CACHE_TAG__="cache";
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        Filter.super.init(filterConfig);
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest=(HttpServletRequest)servletRequest;
        boolean cacheOption=Boolean.parseBoolean(httpServletRequest.getHeader(__CACHE_TAG__));
        if(cacheOption){
            CustomHttpServletRequest customHttpServletRequest = new CustomHttpServletRequest((HttpServletRequest) servletRequest);
            ContentCachingResponseWrapper contentCachingResponseWrapper=new ContentCachingResponseWrapper((HttpServletResponse) servletResponse);
            filterChain.doFilter(customHttpServletRequest, contentCachingResponseWrapper);
            contentCachingResponseWrapper.copyBodyToResponse();
        }else {
            filterChain.doFilter(servletRequest, servletResponse);
        }


    }

    @Override
    public void destroy() {
        Filter.super.destroy();
    }
}
