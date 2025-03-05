package cn.geelato.web.platform.interceptor;

import cn.geelato.core.env.EnvManager;
import cn.geelato.core.env.entity.User;
import cn.geelato.web.platform.PlatformContext;
import cn.geelato.web.platform.Tenant;
import cn.geelato.web.platform.boot.properties.OAuthConfigurationProperties;
import cn.geelato.web.platform.interceptor.annotation.IgnoreVerify;
import cn.geelato.web.platform.oauth.OAuthHelper;
import cn.geelato.web.platform.shiro.OAuth2Token;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.apache.xmlbeans.impl.xb.xsdschema.Public;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class OAuthInterceptor implements HandlerInterceptor {

    private final OAuthConfigurationProperties oAuthConfigurationProperties;
    public OAuthInterceptor(OAuthConfigurationProperties config) {
        oAuthConfigurationProperties=config;
    }

    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) throws Exception {
        // 如果不是映射到方法直接通过
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        // 检查是否有IgnoreToken注释，有则跳过认证
        if (handlerMethod.getMethod().isAnnotationPresent(IgnoreVerify.class)) {
            return true;
        }
        // 从请求头内获取token
        String token = request.getHeader("Authorization");
        // 执行认证
        if (token == null) {
            throw new Exception("invalid oauth token");
        }
        token = token.replace("Bearer ", "");
        // 获取载荷内容
        cn.geelato.web.platform.m.security.entity.User user= OAuthHelper.getUserInfo(oAuthConfigurationProperties.getUrl(), token);
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
