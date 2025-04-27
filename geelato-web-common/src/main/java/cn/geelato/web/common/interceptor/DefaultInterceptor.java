package cn.geelato.web.common.interceptor;

import cn.geelato.core.env.EnvManager;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;

import cn.geelato.web.common.interceptor.annotation.IgnoreVerify;
import cn.geelato.web.common.oauth2.OAuth2Helper;
import cn.geelato.web.common.shiro.OAuth2Token;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class DefaultInterceptor implements HandlerInterceptor {

    private final OAuthConfigurationProperties oAuthConfigurationProperties;

    public DefaultInterceptor(OAuthConfigurationProperties config) {
        oAuthConfigurationProperties = config;
    }

    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) throws Exception {
        String upgradeHeader = request.getHeader("Upgrade");
        if ("websocket".equalsIgnoreCase(upgradeHeader)) {
            return true;
        }
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (handlerMethod.getMethod().isAnnotationPresent(IgnoreVerify.class)) {
            return true;
        }
        String token = request.getHeader("Authorization");
        if (token == null) {
            throw new InvalidTokenException();
        }
        if (token.startsWith("JWTBearer ")) {
            token = token.replace("JWTBearer ", "");
            try {
                DecodedJWT verify = JWTUtil.verify(token);
                String loginName = verify.getClaim("loginName").asString();
                String passWord = verify.getClaim("passWord").asString();
                User currentUser = EnvManager.singleInstance().InitCurrentUser(loginName);
                SecurityContext.setCurrentUser(currentUser);
                SecurityContext.setCurrentTenant(new Tenant(currentUser.getTenantCode()));

                UsernamePasswordToken userToken = new UsernamePasswordToken(loginName, passWord);
                Subject subject = SecurityUtils.getSubject();
                subject.login(userToken);
            } catch (Exception e) {
                throw new OAuthGetUserFailException();
            }
        } else if (token.startsWith("Bearer ")) {
            token = token.replace("Bearer ", "");
            cn.geelato.web.common.security.User user = OAuth2Helper.getUserInfo(oAuthConfigurationProperties.getUrl(), token);
            if (user != null) {
                String loginName = user.getLoginName();
                User currentUser = EnvManager.singleInstance().InitCurrentUser(loginName);
                SecurityContext.setCurrentUser(currentUser);
                SecurityContext.setCurrentTenant(new Tenant(user.getTenantCode()));
                OAuth2Token oauth2Token = new OAuth2Token(token);
                Subject subject = SecurityUtils.getSubject();
                subject.login(oauth2Token);
            } else {
                throw new OAuthGetUserFailException();
            }
        } else {
            throw new InvalidTokenException();
        }
        return true;
    }
}
