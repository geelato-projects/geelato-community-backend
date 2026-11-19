package cn.geelato.web.platform.boot.security;

import cn.geelato.web.common.interceptor.SystemTokenProperties;
import cn.geelato.web.common.interceptor.UnauthorizedException;
import cn.geelato.web.common.security.auth.SystemTokenAuthenticator;
import cn.geelato.web.common.security.spi.TokenAuthenticator;
import cn.geelato.web.common.security.spi.TokenAuthentication;
import cn.geelato.web.common.security.spi.TokenAuthenticationContext;
import cn.geelato.web.common.security.spi.support.TokenAuthenticatorRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.HandlerMethod;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 认证器注册中心：前缀互斥启动期硬失败、无匹配前缀硬失败。 */
class TokenAuthenticatorRegistryTest {

    static class StubAuthenticator implements TokenAuthenticator {
        private final String prefix;

        StubAuthenticator(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Collection<String> schemePrefixes() {
            return List.of(prefix);
        }

        @Override
        public TokenAuthentication authenticate(TokenAuthenticationContext ctx) {
            throw new UnsupportedOperationException();
        }
    }

    private static HandlerMethod handler() throws NoSuchMethodException {
        return new HandlerMethod(new Object(), Object.class.getDeclaredMethod("hashCode"));
    }

    @Test
    void register_duplicatePrefixFailsAtStartup() {
        TokenAuthenticatorRegistry registry = new TokenAuthenticatorRegistry(
                List.of(new StubAuthenticator("Alpha "), new SystemTokenAuthenticator(new SystemTokenProperties())));
        assertThrows(IllegalStateException.class,
                () -> registry.register(new StubAuthenticator("Alpha ")));
    }

    @Test
    void resolve_routesByPrefix() throws Exception {
        SystemTokenAuthenticator systemAuthenticator = new SystemTokenAuthenticator(new SystemTokenProperties());
        TokenAuthenticatorRegistry registry = new TokenAuthenticatorRegistry(List.of(systemAuthenticator));
        assertSame(systemAuthenticator, registry.resolve("SystemToken any-secret"));
    }

    @Test
    void resolve_unknownSchemeThrowsUnauthorized() throws Exception {
        TokenAuthenticatorRegistry registry = new TokenAuthenticatorRegistry(List.of());
        UnauthorizedException e = assertThrows(UnauthorizedException.class,
                () -> registry.resolve("DingtalkCode whatever"));
        assertEquals(401, e.getHttpStatus());
    }
}
