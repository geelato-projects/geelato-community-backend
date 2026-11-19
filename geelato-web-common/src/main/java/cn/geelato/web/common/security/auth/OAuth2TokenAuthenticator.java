package cn.geelato.web.common.security.auth;

import cn.geelato.core.GlobalContext;
import cn.geelato.security.OrgProvider;
import cn.geelato.utils.LocalBoundedCache;
import cn.geelato.web.common.interceptor.OAuthConfigurationProperties;
import cn.geelato.web.common.interceptor.UnauthorizedException;
import cn.geelato.web.common.oauth2.OAuth2Helper;
import cn.geelato.web.common.security.spi.LoginCredential;
import cn.geelato.web.common.security.spi.TokenAuthentication;
import cn.geelato.web.common.security.spi.TokenAuthenticationContext;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * OAuth2 access_token 认证器（Authorization: Bearer &lt;token&gt;）：
 * 远程认证中心校验用户 + 本地 token→用户 缓存。{@link #tokenUserCache}
 * 原挂载于 DefaultSecurityInterceptor，现随本类迁移（{@code OAuth2Realm} 仍读取）。
 */
@Component
public class OAuth2TokenAuthenticator extends AbstractTokenAuthenticator {

    public static final String OAUTH2_PREFIX = "Bearer ";
    private static final long CACHE_TTL_MILLIS = 30 * 60 * 1000L;
    private static final String ANONYMOUS_FIXED_PASSWORD = GlobalContext.getAnonymousPwd();

    public static final LocalBoundedCache<String, cn.geelato.meta.User> tokenUserCache =
            new LocalBoundedCache<>("token-user", CACHE_TTL_MILLIS, 100_000);

    private final OAuthConfigurationProperties properties;

    public OAuth2TokenAuthenticator(OAuthConfigurationProperties properties, OrgProvider orgProvider) {
        super(orgProvider);
        this.properties = properties;
    }

    @Override
    public Collection<String> schemePrefixes() {
        return List.of(OAUTH2_PREFIX);
    }

    @Override
    public TokenAuthentication authenticate(TokenAuthenticationContext ctx) {
        String accessToken = ctx.stripped(OAUTH2_PREFIX);
        try {
            cn.geelato.meta.User user = tokenUserCache.get(accessToken);
            if (user == null) {
                user = OAuth2Helper.getUserInfo(properties.getUrl(), accessToken);
                if (user != null) {
                    tokenUserCache.put(accessToken, user);
                }
            }
            if (user == null) {
                throw new UnauthorizedException("OAuth2 用户信息获取失败");
            }
            String tenantCode = user.getTenantCode();
            if (tenantCode == null || tenantCode.trim().isEmpty()) {
                tenantCode = GlobalContext.getDefaultTenantCode();
            }
            cn.geelato.security.User currentUser = loadUser(user.getLoginName(), tenantCode);
            return new TokenAuthentication(currentUser, ANONYMOUS_FIXED_PASSWORD,
                    new LoginCredential(LoginCredential.TYPE_OAUTH2, accessToken, accessToken));
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            throw new UnauthorizedException("OAuth2 认证失败: " + e.getMessage());
        }
    }
}
