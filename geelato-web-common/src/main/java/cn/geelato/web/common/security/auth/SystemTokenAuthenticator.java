package cn.geelato.web.common.security.auth;

import cn.geelato.core.GlobalContext;
import cn.geelato.security.User;
import cn.geelato.web.common.interceptor.SystemTokenProperties;
import cn.geelato.web.common.interceptor.UnauthorizedException;
import cn.geelato.web.common.interceptor.annotation.AllowSystemAccess;
import cn.geelato.web.common.security.spi.LoginCredential;
import cn.geelato.web.common.security.spi.TokenAuthentication;
import cn.geelato.web.common.security.spi.TokenAuthenticationContext;
import cn.geelato.web.common.security.spi.TokenAuthenticator;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * 外部系统固定令牌认证器：Authorization: SystemToken &lt;固定密钥&gt;。
 * 仅对标注 {@link AllowSystemAccess} 的方法生效（原 preHandle 前置门禁迁入本类），
 * 认证通过后以虚拟系统主体（systemPrincipal=true）运行，不关联平台用户、不计在线用户。
 */
@Component
public class SystemTokenAuthenticator implements TokenAuthenticator {

    public static final String SYSTEM_TOKEN_PREFIX = "SystemToken ";

    private final SystemTokenProperties properties;

    public SystemTokenAuthenticator(SystemTokenProperties properties) {
        this.properties = properties;
    }

    @Override
    public Collection<String> schemePrefixes() {
        return List.of(SYSTEM_TOKEN_PREFIX);
    }

    /** 方法级门禁：仅 @AllowSystemAccess 方法可用 SystemToken，先于缓存还原执行。 */
    @Override
    public void preAuthCheck(TokenAuthenticationContext ctx) {
        if (!ctx.handlerMethod().getMethod().isAnnotationPresent(AllowSystemAccess.class)) {
            throw new UnauthorizedException("SystemToken 仅限标注 @AllowSystemAccess 的接口调用");
        }
    }

    @Override
    public TokenAuthentication authenticate(TokenAuthenticationContext ctx) {
        String token = ctx.stripped(SYSTEM_TOKEN_PREFIX);
        if (!properties.matches(token)) {
            throw new UnauthorizedException("SystemToken 校验失败");
        }
        User currentUser = new User();
        currentUser.setUserId("system");
        currentUser.setLoginName("system");
        currentUser.setUserName("system");
        currentUser.setTenantCode(GlobalContext.getDefaultTenantCode());
        currentUser.setSystemPrincipal(true);
        return new TokenAuthentication(currentUser, token,
                new LoginCredential(LoginCredential.TYPE_SYSTEM_TOKEN, token, token), false);
    }
}
