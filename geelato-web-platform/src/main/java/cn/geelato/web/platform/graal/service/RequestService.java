package cn.geelato.web.platform.graal.service;

import cn.geelato.core.graal.GraalFunction;
import cn.geelato.core.graal.GraalService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@GraalService(name = "request",built = "true", descrption = "获取当前会话请求数据")
public class RequestService {


    @GraalFunction(example = "$gl.request.getHeaders()", description = "获取当前请求所有Header键值")
    public Map<String,String> getHeaders(){
        HttpServletRequest request = ((ServletRequestAttributes)RequestContextHolder.currentRequestAttributes()).getRequest();
        Map<String,String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            headers.put(headerName, headerValue);
        }
        return headers;
    }

    @GraalFunction(example = "$gl.request.getHeader({key})", description = "根据键获取当前请求Header值")
    public String getHeader(String key){
        return getHeaders().get(key);
    }
    @GraalFunction(example = "$gl.request.getCookies()", description = "获取当前请求所有Cookie键值")
    public Map<String,String> getCookies(){
        HttpServletRequest request = ((ServletRequestAttributes)RequestContextHolder.currentRequestAttributes()).getRequest();
        Map<String,String> cookieMap = new HashMap<>();
        Cookie[] cookies = request.getCookies();
        if(cookies != null){
            for (Cookie cookie : cookies) {
                cookieMap.put(cookie.getName(),cookie.getValue());
            }
        }
        return cookieMap;
    }
    @GraalFunction(example = "$gl.request.getCookie({key})", description = "根据键获取当前请求Cookie值")
    public String getCookie(String key){
        return getCookies().get(key);
    }

    @GraalFunction(example = "$gl.request.getRequestParameters()", description = "获取当前请求的全部URL参数键值")
    public Map<String,String> getRequestParameters(){
        HttpServletRequest request = ((ServletRequestAttributes)RequestContextHolder.currentRequestAttributes()).getRequest();
        Map<String,String> params = new HashMap<>();
        Enumeration<String> parameterNames = request.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String parameterName = parameterNames.nextElement();
            String parameterValue = request.getParameter(parameterName);
            params.put(parameterName, parameterValue);
        }
        return params;
    }

    @GraalFunction(example = "$gl.request.getRequestParameter({key})", description = "根据键获取当前请求的URL参数值")
    public String getRequestParameter(String key){
        return getRequestParameters().get(key);
    }
}
