package cn.geelato.web.platform.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import cn.geelato.web.platform.cache.CacheService;
import cn.geelato.web.platform.cache.CacheServiceImpl;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.io.BufferedReader;
import java.io.IOException;

public class CacheInterceptor implements HandlerInterceptor {

    CacheService<Object> cacheService=new CacheServiceImpl<>();
    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) throws Exception {
//        boolean cacheOption= Boolean.parseBoolean(request.getHeader("cache"));
//        if(cacheOption){
//            String gql=getGql(request);
//            if(cacheService.getCache(gql)!=null) {
//                response.getWriter().write(cacheService.getCache(gql).toString());
//                return false;
//            }
//        }
        return true;
    }

    @Override
    public void postHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler, ModelAndView modelAndView) throws Exception {
//        CustomHttpServletResponse customHttpServletResponse=(CustomHttpServletResponse)response;
//        boolean cacheOption= Boolean.parseBoolean(request.getHeader("cache"));
//        if(cacheOption){
//            String gql=getGql(request);
//            if(cacheService.getCache(gql)==null) {
//                String data= customHttpServletResponse.getData();
//                cacheService.putCache(gql,data);
//                customHttpServletResponse.getWriter().write(data);
//            }
//        }
    }



    @Override
    public void afterCompletion(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler, Exception ex) throws Exception {

    }

    private String getGql(HttpServletRequest request) {
        StringBuilder stringBuilder = new StringBuilder();
        BufferedReader br = null;
        try {
            br = request.getReader();
        } catch (IOException e) {
        }
        String str;
        try {
            while ((str = br.readLine()) != null) {
                stringBuilder.append(str);
            }
        } catch (IOException e) {
        }
        return stringBuilder.toString();
    }
}
