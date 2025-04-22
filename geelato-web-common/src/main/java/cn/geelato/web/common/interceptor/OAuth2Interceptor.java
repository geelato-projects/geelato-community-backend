package cn.geelato.web.common.interceptor;

import cn.geelato.core.env.EnvManager;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import cn.geelato.web.common.oauth2.OAuth2Helper;
import cn.geelato.web.common.shiro.OAuth2Token;
import cn.geelato.web.common.interceptor.annotation.IgnoreVerify;
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
            throw new InvalidTokenException("InvalidOAuth2TokenException");
        }
        token = token.replace("Bearer ", "");
        User user= OAuth2Helper.getUserInfo(oAuthConfigurationProperties.getUrl(), token);
        if (user != null) {
            String loginName  = user.getLoginName();
            User currentUser = EnvManager.singleInstance().InitCurrentUser(loginName);
            SecurityContext.setCurrentUser(currentUser);
            SecurityContext.setCurrentTenant(new Tenant(user.getTenantCode()));
            OAuth2Token oauth2Token = new OAuth2Token(token);
            Subject subject = SecurityUtils.getSubject();
            subject.login(oauth2Token);
        }else {
            throw new OAuthGetUserFailException();
        }
        return true;
    }
}
