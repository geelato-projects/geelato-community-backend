package cn.geelato.web.platform.boot.Inteceptor;

import cn.geelato.web.platform.cache.CacheService;
import cn.geelato.web.platform.cache.CacheServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.BufferedReader;
import java.io.IOException;

@Slf4j
public class CacheInterceptor implements HandlerInterceptor {
    CacheService<Object> cacheService = new CacheServiceImpl<>();

    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) throws Exception {
        boolean cacheOption = Boolean.parseBoolean(request.getHeader("cache"));
        if (cacheOption) {
            String gql = resolveGql(request);
            if (cacheService.getCache(gql) != null) {
                response.getWriter().write(cacheService.getCache(gql).toString());
                return false;
            }
        }
        return true;
    }

    @Override
    public void postHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler, ModelAndView modelAndView) throws Exception {
        boolean cacheOption = Boolean.parseBoolean(request.getHeader("cache"));
        if (cacheOption) {
            ContentCachingResponseWrapper responseWrapper = (ContentCachingResponseWrapper) response;
            String gql = resolveGql(request);
            if (cacheService.getCache(gql) == null) {
                String data = new String(responseWrapper.getContentAsByteArray());
                cacheService.putCache(gql, data);
            }
        }
    }

    private String resolveGql(HttpServletRequest request) throws Exception {
        StringBuilder stringBuilder = new StringBuilder();
        String str;
        try (BufferedReader br = request.getReader()) {
            if (br != null) {
                while ((str = br.readLine()) != null) {
                    stringBuilder.append(str);
                }
            }
        } catch (IOException e) {
            throw new Exception("未能从httpServletRequest中获取gql的内容");
        }

        return stringBuilder.toString();
    }

}
