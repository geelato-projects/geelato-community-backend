package cn.geelato.web.common.security.auth;

import cn.geelato.core.GlobalContext;
import cn.geelato.security.OrgProvider;
import cn.geelato.security.User;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.common.interceptor.UnauthorizedException;
import cn.geelato.web.common.security.JWTUtil;
import cn.geelato.web.common.security.spi.LoginCredential;
import cn.geelato.web.common.security.spi.TokenAuthentication;
import cn.geelato.web.common.security.spi.TokenAuthenticationContext;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * 自签 JWT 认证器：JWTBearer（正式）与 Anonymous（匿名）两种方案。
 * 两者均为本平台签发的混淆 JWT，共用验签、claim 解析与用户装载；
 * Anonymous 仅多一道匿名口令 claim 校验，凭证固定为全局匿名密码。
 */
@Component
public class JwtTokenAuthenticator extends AbstractTokenAuthenticator {

    public static final String JWT_PREFIX = "JWTBearer ";
    public static final String ANONYMOUS_PREFIX = "Anonymous ";
    private static final String ANONYMOUS_FIXED_PASSWORD = GlobalContext.getAnonymousPwd();

    public JwtTokenAuthenticator(OrgProvider orgProvider) {
        super(orgProvider);
    }

    @Override
    public Collection<String> schemePrefixes() {
        return List.of(JWT_PREFIX, ANONYMOUS_PREFIX);
    }

    @Override
    public TokenAuthentication authenticate(TokenAuthenticationContext ctx) {
        boolean anonymous = ctx.rawToken().startsWith(ANONYMOUS_PREFIX);
        String prefix = anonymous ? ANONYMOUS_PREFIX : JWT_PREFIX;
        try {
            DecodedJWT verify = JWTUtil.verify(ctx.stripped(prefix));
            String password;
            if (anonymous) {
                String anonymousClaim = verify.getClaim("anonymous").asString();
                if (StringUtils.isEmpty(anonymousClaim) || !ANONYMOUS_FIXED_PASSWORD.equals(anonymousClaim)) {
                    throw new UnauthorizedException("匿名口令校验失败");
                }
                password = ANONYMOUS_FIXED_PASSWORD;
            } else {
                password = verify.getClaim("passWord").asString();
            }
            String loginName = verify.getClaim("loginName").asString();
            String orgId = verify.getClaim("orgId").asString();
            String tenantCode = verify.getClaim("tenantCode").asString();
            if (StringUtils.isEmpty(loginName)) {
                throw new UnauthorizedException("令牌缺少 loginName 声明");
            }
            User currentUser = loadUser(loginName, tenantCode);
            setupOrgInfo(currentUser, orgId);
            return new TokenAuthentication(currentUser, password,
                    new LoginCredential(LoginCredential.TYPE_USERNAME_PASSWORD, loginName, password));
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            throw new UnauthorizedException(anonymous ? "匿名令牌校验失败: " + e.getMessage()
                    : "JWT 令牌校验失败: " + e.getMessage());
        }
    }
}
