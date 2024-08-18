package cn.geelato.web.platform.interceptor;
import cn.geelato.web.platform.utils.GqlUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import cn.geelato.web.platform.cache.CacheService;
import cn.geelato.web.platform.cache.CacheServiceImpl;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Arrays;

public class CacheInterceptor implements HandlerInterceptor {

    CacheService<Object> cacheService=new CacheServiceImpl<>();
    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) throws Exception {
        boolean cacheOption= Boolean.parseBoolean(request.getHeader("cache"));
        if(cacheOption){
            String gql= GqlUtil.resolveGql(request);
            if(cacheService.getCache(gql)!=null) {
                response.getWriter().write(cacheService.getCache(gql).toString());
                return false;
            }
        }
        return true;
    }

    @Override
    public void postHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler, ModelAndView modelAndView) throws Exception {
        boolean cacheOption= Boolean.parseBoolean(request.getHeader("cache"));
        if(cacheOption){
            ContentCachingResponseWrapper responseWrapper = (ContentCachingResponseWrapper) response;
            String gql=GqlUtil.resolveGql(request);
            if(cacheService.getCache(gql)==null) {
                String data= new String(responseWrapper.getContentAsByteArray());
                cacheService.putCache(gql,data);
            }
        }
    }

}
