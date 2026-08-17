package cn.geelato.web.platform.boot.security;

import cn.geelato.security.SecurityContext;
import cn.geelato.web.common.interceptor.DefaultSecurityInterceptor;
import cn.geelato.web.common.interceptor.OAuthConfigurationProperties;
import cn.geelato.web.common.interceptor.SystemTokenProperties;
import cn.geelato.web.common.interceptor.UnauthorizedException;
import cn.geelato.web.common.interceptor.annotation.AllowSystemAccess;
import cn.geelato.web.common.interceptor.annotation.IgnoreVerify;
import cn.geelato.web.common.shiro.SystemTokenRealm;
import cn.geelato.web.common.shiro.SystemTokenToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SystemToken 固定令牌认证单元测试——不依赖 Spring 容器与数据库，
 * 通过 ThreadContext 绑定仅含 {@link SystemTokenRealm} 的 Shiro 环境。
 * <p>
 * 重点用例：同一固定令牌在已标注 @AllowSystemAccess 的接口认证成功（进入上下文缓存）后，
 * 调未标注接口必须被注解前置校验拒绝，不能借缓存还原绕过注解限制。
 */
class SystemTokenAuthenticationTest {

    static class DemoController {
        @AllowSystemAccess
        public void annotated() {
        }

        public void plain() {
        }

        @IgnoreVerify
        public void ignored() {
        }
    }

    private final SystemTokenProperties properties = new SystemTokenProperties();
    private final DefaultSecurityInterceptor interceptor =
            new DefaultSecurityInterceptor(new OAuthConfigurationProperties(), null);

    @BeforeEach
    void setUp() {
        interceptor.setSystemTokenProperties(properties);
        ThreadContext.bind(new DefaultSecurityManager(new SystemTokenRealm(properties)));
    }

    @AfterEach
    void tearDown() {
        // Shiro 2.x：清空线程上下文（等价旧版 unbindAll），避免跨用例污染
        ThreadContext.remove();
        SecurityContext.clear();
    }

    private MockHttpServletRequest request(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/notification/send");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    private HandlerMethod handler(String methodName) throws NoSuchMethodException {
        Method method = DemoController.class.getMethod(methodName);
        return new HandlerMethod(new DemoController(), method);
    }

    @Test
    void matches_onlyConfiguredTokenPasses() {
        assertTrue(properties.matches(properties.getToken()));
        assertFalse(properties.matches("wrong-token"));
        assertFalse(properties.matches(""));
        assertFalse(properties.matches(null));
        properties.setToken("overridden");
        assertTrue(properties.matches("overridden"));
        assertFalse(properties.matches(properties.DEFAULT_TOKEN));
    }

    @Test
    void realm_onlySupportsSystemTokenToken() {
        SystemTokenRealm realm = new SystemTokenRealm(properties);
        assertTrue(realm.supports(new SystemTokenToken("any")));
        assertFalse(realm.supports(new UsernamePasswordToken("user", "pwd")));
    }

    @Test
    void preHandle_systemTokenOnAnnotatedMethodAuthenticatesAsSystemPrincipal() throws Exception {
        boolean result = interceptor.preHandle(
                request("SystemToken " + properties.getToken()),
                new MockHttpServletResponse(), handler("annotated"));
        assertTrue(result);
        assertNotNull(SecurityContext.getCurrentUser());
        assertEquals("system", SecurityContext.getCurrentUser().getUserId());
        assertTrue(SecurityContext.isSystemPrincipal());
        assertNotNull(SecurityContext.getCurrentTenant());
    }

    @Test
    void preHandle_secondRequestRestoredFromCache() throws Exception {
        properties.setToken("cache-secret");
        String raw = "SystemToken cache-secret";
        assertTrue(interceptor.preHandle(request(raw), new MockHttpServletResponse(), handler("annotated")));
        // 模拟新请求：生产环境每个 HTTP 请求都是新的 Subject（保留 SecurityManager）
        ThreadContext.unbindSubject();
        SecurityContext.clear();
        // 第二次请求走 tryRestoreFromCache 分支（SystemTokenToken 重新 subject.login）
        assertTrue(interceptor.preHandle(request(raw), new MockHttpServletResponse(), handler("annotated")));
    }

    @Test
    void preHandle_cachedSystemTokenCannotBypassAnnotationOnOtherMethod() throws Exception {
        properties.setToken("guard-secret");
        String raw = "SystemToken guard-secret";
        assertTrue(interceptor.preHandle(request(raw), new MockHttpServletResponse(), handler("annotated")));
        ThreadContext.unbindSubject();
        SecurityContext.clear();
        // 同一令牌调未标注方法：注解校验先于缓存还原，必须拒绝
        assertThrows(UnauthorizedException.class, () ->
                interceptor.preHandle(request(raw), new MockHttpServletResponse(), handler("plain")));
    }

    @Test
    void preHandle_systemTokenOnNonAnnotatedMethodRejected() throws Exception {
        assertThrows(UnauthorizedException.class, () ->
                interceptor.preHandle(request("SystemToken " + properties.getToken()),
                        new MockHttpServletResponse(), handler("plain")));
    }

    @Test
    void preHandle_wrongTokenOnAnnotatedMethodRejected() throws Exception {
        assertThrows(UnauthorizedException.class, () ->
                interceptor.preHandle(request("SystemToken wrong-token"),
                        new MockHttpServletResponse(), handler("annotated")));
    }

    @Test
    void preHandle_missingHeaderRejectedAsBefore() throws Exception {
        assertThrows(UnauthorizedException.class, () ->
                interceptor.preHandle(request(null), new MockHttpServletResponse(), handler("plain")));
    }

    @Test
    void preHandle_ignoreVerifyStillSkipsAuth() throws Exception {
        assertTrue(interceptor.preHandle(request(null), new MockHttpServletResponse(), handler("ignored")));
    }

    @Test
    void preHandle_nonSystemTokenFlowUnaffected() throws Exception {
        // 非法 JWTBearer 令牌照旧被拒，SystemToken 分支不干扰既有认证链
        assertThrows(UnauthorizedException.class, () ->
                interceptor.preHandle(request("JWTBearer garbage"),
                        new MockHttpServletResponse(), handler("plain")));
    }
}
