package cn.geelato.web.platform.boot.security;

import cn.geelato.core.env.EnvManager;
import cn.geelato.core.env.EnvStore;
import cn.geelato.core.env.entity.SysConfig;
import cn.geelato.security.OrgProvider;
import cn.geelato.security.Permission;
import cn.geelato.security.User;
import cn.geelato.security.UserOrg;
import cn.geelato.security.UserProvider;
import cn.geelato.security.UserRole;
import cn.geelato.web.common.interceptor.UnauthorizedException;
import cn.geelato.web.common.security.auth.WeixinCodeTokenAuthenticator;
import cn.geelato.web.common.security.spi.LoginCredential;
import cn.geelato.web.common.security.spi.TokenAuthentication;
import cn.geelato.web.common.security.spi.TokenAuthenticationContext;
import cn.geelato.web.common.security.spi.WeixinCodeResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WeixinCode 备用通道认证器单元测试：resolver 可选装配、code→unionid 缓存、
 * unionid 反查用户（weixinUnionId 扩展键链路）、失败分支统一 401。
 */
class WeixinCodeTokenAuthenticatorTest {

    static class StubEnvStore implements EnvStore {
        @Override
        public List<SysConfig> loadAllSysConfig() {
            return List.of();
        }

        @Override
        public SysConfig loadSysConfig(String configKey) {
            return null;
        }

        @Override
        public User loadUser(String loginName, String tenantCode) {
            User user = new User();
            user.setUserId("3751618121485025347");
            user.setLoginName(loginName);
            user.setUserName("张三");
            user.setTenantCode(tenantCode);
            user.setEnableStatus(1);
            return user;
        }

        @Override
        public List<UserRole> loadUserRoles(String userId) {
            return List.of();
        }

        @Override
        public List<UserOrg> loadUserOrgs(String userId) {
            return List.of();
        }

        @Override
        public List<Permission> loadDataPermissions(String userId) {
            return List.of();
        }

        @Override
        public List<Permission> loadElementPermissions(String userId) {
            return List.of();
        }
    }

    static class StubUserProvider implements UserProvider {
        User extendKeyUser;

        @Override
        public User getUser(String userId) {
            return null;
        }

        @Override
        public User getUserByExtendKey(String extendKey, String type) {
            return extendKeyUser;
        }

        @Override
        public void refresh() {
        }
    }

    private StubUserProvider userProvider;
    private WeixinCodeTokenAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        EnvManager.singleInstance().setEnvStore(new StubEnvStore());
        userProvider = new StubUserProvider();
    }

    @AfterEach
    void tearDown() {
        EnvManager.singleInstance().setEnvStore(null);
    }

    private WeixinCodeTokenAuthenticator newAuthenticator(WeixinCodeResolver resolver) {
        return new WeixinCodeTokenAuthenticator((OrgProvider) null, userProvider, new ObjectProvider<>() {
            @Override
            public WeixinCodeResolver getObject() {
                return resolver;
            }

            @Override
            public WeixinCodeResolver getObject(Object... args) {
                return resolver;
            }

            @Override
            public WeixinCodeResolver getIfAvailable() {
                return resolver;
            }

            @Override
            public WeixinCodeResolver getIfUnique() {
                return resolver;
            }
        });
    }

    private TokenAuthenticationContext ctx(String rawToken) {
        return new TokenAuthenticationContext(rawToken, new MockHttpServletRequest(), null);
    }

    @Test
    void authenticate_resolverMissingFailsWithExplicitError() {
        WeixinCodeTokenAuthenticator withoutResolver = newAuthenticator(null);
        UnauthorizedException e = assertThrows(UnauthorizedException.class,
                () -> withoutResolver.authenticate(ctx("WeixinCode any-code")));
        assertTrue(e.getMessage().contains("WeixinCodeResolver"));
    }

    @Test
    void authenticate_resolvesUnionIdAndBindsPlatformUser() {
        User boundUser = new User();
        boundUser.setLoginName("cs_zhangsan");
        boundUser.setTenantCode("geelato");
        userProvider.extendKeyUser = boundUser;
        WeixinCodeResolver resolver = jsCode -> "union-abc";
        authenticator = newAuthenticator(resolver);

        TokenAuthentication result = authenticator.authenticate(ctx("WeixinCode fresh-code"));

        assertEquals(LoginCredential.TYPE_WEIXIN_UNION_ID, result.login().type());
        assertEquals("union-abc", result.login().principal());
        assertEquals("cs_zhangsan", result.user().getLoginName());
        assertTrue(result.trackOnline());
    }

    @Test
    void authenticate_unboundUnionIdRejected() {
        userProvider.extendKeyUser = null;
        authenticator = newAuthenticator(jsCode -> "union-nobody");

        assertThrows(UnauthorizedException.class,
                () -> authenticator.authenticate(ctx("WeixinCode code-unbound")));
    }

    @Test
    void authenticate_resolverFailurePropagates() {
        authenticator = newAuthenticator(jsCode -> {
            throw new UnauthorizedException("登录凭证已被使用，请重新获取");
        });

        UnauthorizedException e = assertThrows(UnauthorizedException.class,
                () -> authenticator.authenticate(ctx("WeixinCode consumed-code")));
        assertTrue(e.getMessage().contains("已被使用"));
    }

    @Test
    void authenticate_emptyCodeRejected() {
        authenticator = newAuthenticator(jsCode -> "union-abc");

        assertThrows(UnauthorizedException.class, () -> authenticator.authenticate(ctx("WeixinCode ")));
    }

    @Test
    void authenticate_sameCodeServedFromCacheWithoutSecondResolve() {
        userProvider.extendKeyUser = new User();
        userProvider.extendKeyUser.setLoginName("cs_zhangsan");
        userProvider.extendKeyUser.setTenantCode("geelato");
        AtomicInteger resolveCalls = new AtomicInteger();
        authenticator = newAuthenticator(jsCode -> {
            resolveCalls.incrementAndGet();
            return "union-cache";
        });

        authenticator.authenticate(ctx("WeixinCode retry-same-code"));
        authenticator.authenticate(ctx("WeixinCode retry-same-code"));

        assertEquals(1, resolveCalls.get(), "同一 code 第二次必须走缓存，不得二次外呼微信");
    }
}
