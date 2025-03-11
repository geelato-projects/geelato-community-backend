package cn.geelato.web.platform.interceptor;

import cn.geelato.core.env.EnvManager;
import cn.geelato.core.env.entity.User;
import cn.geelato.web.platform.PlatformContext;
import cn.geelato.web.platform.Tenant;
import cn.geelato.web.platform.boot.properties.OAuthConfigurationProperties;
import cn.geelato.web.platform.interceptor.annotation.IgnoreVerify;
import cn.geelato.web.platform.oauth2.OAuth2Helper;
import cn.geelato.web.platform.shiro.OAuth2Token;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class OAuth2Interceptor implements HandlerInterceptor {

    private final OAuthConfigurationProperties oAuthConfigurationProperties;
    public OAuth2Interceptor(OAuthConfigurationProperties config) {
        oAuthConfigurationProperties=config;
    }

    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (handlerMethod.getMethod().isAnnotationPresent(IgnoreVerify.class)) {
            return true;
        }
        String token = request.getHeader("Authorization");
        if (token == null) {
            throw new Exception("invalid oauth token");
        }
        token = token.replace("Bearer ", "");
        cn.geelato.web.platform.m.security.entity.User user= OAuth2Helper.getUserInfo(oAuthConfigurationProperties.getUrl(), token);
        if (user != null) {
            String loginName  = user.getLoginName();
            User currentUser = EnvManager.singleInstance().InitCurrentUser(loginName);
            PlatformContext.setCurrentUser(currentUser);
            PlatformContext.setCurrentTenant(new Tenant(user.getTenantCode()));
            OAuth2Token oauth2Token = new OAuth2Token(token);
            Subject subject = SecurityUtils.getSubject();
            subject.login(oauth2Token);
        }else {
            throw new Exception("oauth get user fail!");
        }
        return true;
    }
}
