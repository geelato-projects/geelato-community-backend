package cn.geelato.web.common.security.auth;

import cn.geelato.core.GlobalContext;
import cn.geelato.security.OrgProvider;
import cn.geelato.security.User;
import cn.geelato.security.UserProvider;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.common.interceptor.UnauthorizedException;
import cn.geelato.web.common.security.spi.LoginCredential;
import cn.geelato.web.common.security.spi.TokenAuthentication;
import cn.geelato.web.common.security.spi.TokenAuthenticationContext;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * 微信扩展键认证器：WeixinUnionId / WeixinWorkUserId 两种方案。
 * 以裸扩展键（非 JWT）为凭证，经 UserProvider 反查平台用户。
 */
@Component
public class WeixinTokenAuthenticator extends AbstractTokenAuthenticator {

    public static final String WEIXIN_UNION_ID_PREFIX = "WeixinUnionId ";
    public static final String WEIXIN_WORK_USER_ID_PREFIX = "WeixinWorkUserId ";
    private static final String ANONYMOUS_FIXED_PASSWORD = GlobalContext.getAnonymousPwd();

    private final UserProvider userProvider;

    public WeixinTokenAuthenticator(OrgProvider orgProvider, UserProvider userProvider) {
        super(orgProvider);
        this.userProvider = userProvider;
    }

    @Override
    public Collection<String> schemePrefixes() {
        return List.of(WEIXIN_UNION_ID_PREFIX, WEIXIN_WORK_USER_ID_PREFIX);
    }

    @Override
    public TokenAuthentication authenticate(TokenAuthenticationContext ctx) {
        boolean unionId = ctx.rawToken().startsWith(WEIXIN_UNION_ID_PREFIX);
        String prefix = unionId ? WEIXIN_UNION_ID_PREFIX : WEIXIN_WORK_USER_ID_PREFIX;
        String extendKey = ctx.stripped(prefix);
        String extendType = unionId ? "weixinUnionId" : "weixinWorkUserId";
        if (StringUtils.isEmpty(extendKey)) {
            throw new UnauthorizedException("微信扩展键为空");
        }
        try {
            User user = userProvider.getUserByExtendKey(extendKey, extendType);
            if (user == null || StringUtils.isEmpty(user.getLoginName())) {
                throw new UnauthorizedException("微信扩展键未匹配到平台用户: " + extendType);
            }
            User currentUser = loadUser(user.getLoginName(), user.getTenantCode());
            return new TokenAuthentication(currentUser, ANONYMOUS_FIXED_PASSWORD,
                    new LoginCredential(unionId ? LoginCredential.TYPE_WEIXIN_UNION_ID
                            : LoginCredential.TYPE_WEIXIN_WORK_USER_ID, extendKey, extendKey));
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            throw new UnauthorizedException("微信扩展键认证失败: " + e.getMessage());
        }
    }
}
