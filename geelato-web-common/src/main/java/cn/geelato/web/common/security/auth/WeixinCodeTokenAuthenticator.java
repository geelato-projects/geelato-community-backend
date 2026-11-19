package cn.geelato.web.common.security.auth;

import cn.geelato.core.GlobalContext;
import cn.geelato.security.OrgProvider;
import cn.geelato.security.User;
import cn.geelato.security.UserProvider;
import cn.geelato.utils.LocalBoundedCache;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.common.interceptor.UnauthorizedException;
import cn.geelato.web.common.security.spi.LoginCredential;
import cn.geelato.web.common.security.spi.TokenAuthentication;
import cn.geelato.web.common.security.spi.TokenAuthenticationContext;
import cn.geelato.web.common.security.spi.WeixinCodeResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * 微信小程序登录凭证认证器（Authorization: WeixinCode &lt;js_code&gt;，备用通道）。
 * <p>
 * 平台只负责通用编排：code→unionid 缓存（5 分钟，code 一次性，覆盖前端超时重试/
 * 并发复用同一 code 的场景，避免 40163）、unionid 反查平台用户（复用 weixinUnionId
 * 扩展键链路）、会话建立走既有 {@code WeixinUnionIdRealm}；code2session 细节由上层
 * 宿主实现 {@link WeixinCodeResolver} 注册为 Spring Bean 提供无实现时本通道统一 401。
 * <p>
 * 定位为降级/兼容通道：每请求（缓存未命中时）需外呼微信，推荐业务走 token 通道。
 */
@Component
public class WeixinCodeTokenAuthenticator extends AbstractTokenAuthenticator {

    public static final String WEIXIN_CODE_PREFIX = "WeixinCode ";
    public static final long CODE_TTL_MILLIS = 5 * 60 * 1000L;
    private static final String ANONYMOUS_FIXED_PASSWORD = GlobalContext.getAnonymousPwd();

    /** code → unionid。code 一次性，同 code 重试由缓存兜底；unionid 每次仍实时反查用户（绑定/解绑即时生效）。 */
    private static final LocalBoundedCache<String, String> codeUnionIdCache =
            new LocalBoundedCache<>("weixin-code-unionid", CODE_TTL_MILLIS, 10_000);

    private final UserProvider userProvider;
    private final WeixinCodeResolver resolver;

    public WeixinCodeTokenAuthenticator(OrgProvider orgProvider, UserProvider userProvider,
                                        ObjectProvider<WeixinCodeResolver> resolverProvider) {
        super(orgProvider);
        this.userProvider = userProvider;
        this.resolver = resolverProvider.getIfAvailable();
    }

    @Override
    public Collection<String> schemePrefixes() {
        return List.of(WEIXIN_CODE_PREFIX);
    }

    @Override
    public TokenAuthentication authenticate(TokenAuthenticationContext ctx) {
        if (resolver == null) {
            throw new UnauthorizedException("WeixinCode 通道未启用：宿主未提供 WeixinCodeResolver 实现");
        }
        String code = ctx.stripped(WEIXIN_CODE_PREFIX);
        if (StringUtils.isEmpty(code)) {
            throw new UnauthorizedException("微信登录凭证为空");
        }
        try {
            String unionId = codeUnionIdCache.get(code);
            if (unionId == null) {
                unionId = resolver.resolveUnionId(code);
                codeUnionIdCache.put(code, unionId);
            }
            User user = userProvider.getUserByExtendKey(unionId, "weixinUnionId");
            if (user == null || StringUtils.isEmpty(user.getLoginName())) {
                throw new UnauthorizedException("微信身份未绑定平台账号");
            }
            User currentUser = loadUser(user.getLoginName(), user.getTenantCode());
            return new TokenAuthentication(currentUser, ANONYMOUS_FIXED_PASSWORD,
                    new LoginCredential(LoginCredential.TYPE_WEIXIN_UNION_ID, unionId, unionId));
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            throw new UnauthorizedException("微信登录凭证认证失败: " + e.getMessage());
        }
    }
}
