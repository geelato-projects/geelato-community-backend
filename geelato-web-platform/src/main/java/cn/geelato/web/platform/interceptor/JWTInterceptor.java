package cn.geelato.web.platform.interceptor;

import cn.geelato.core.env.EnvManager;
import cn.geelato.core.env.entity.User;
import cn.geelato.web.platform.PlatformContext;
import cn.geelato.web.platform.Tenant;
import cn.geelato.web.platform.interceptor.annotation.IgnoreVerify;
import cn.geelato.web.platform.m.security.service.JWTUtil;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * @author geemeta
 */
public class JWTInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) throws Exception {
        String upgradeHeader = request.getHeader("Upgrade");
        if("websocket".equalsIgnoreCase(upgradeHeader)){
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
            throw new Exception("invalid token");
        }
        token = token.replace("Bearer ", "");
        DecodedJWT verify = JWTUtil.verify(token);
        String loginName = verify.getClaim("loginName").asString();
        String passWord = verify.getClaim("passWord").asString();
        User currentUser = EnvManager.singleInstance().InitCurrentUser(loginName);
        PlatformContext.setCurrentUser(currentUser);
        PlatformContext.setCurrentTenant(new Tenant("geelato"));

        UsernamePasswordToken userToken = new UsernamePasswordToken(loginName, passWord);
        Subject subject = SecurityUtils.getSubject();
        subject.login(userToken);

        return true;
    }

}
