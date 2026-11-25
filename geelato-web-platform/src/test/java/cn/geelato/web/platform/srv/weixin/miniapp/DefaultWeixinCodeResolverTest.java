package cn.geelato.web.platform.srv.weixin.miniapp;

import cn.geelato.lang.exception.CoreException;
import cn.geelato.web.common.interceptor.UnauthorizedException;
import cn.geelato.web.platform.boot.properties.WeixinMiniAppConfigurationProperties;
import cn.geelato.web.platform.srv.weixin.miniapp.spi.DefaultWeixinCodeResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 平台默认 js_code 解析（DefaultWeixinCodeResolver）单元测试：微信 errcode → 模块错误码映射
 * （40029→20006/40163→20007/45011→20015/其他→20006）、unionid 缺失→20010、IO 失败→20006、
 * 联调调试 code（debug-enabled）解析。覆写 fetchSessionResponse 桩替 HTTP，不发起真实网络调用。
 */
class DefaultWeixinCodeResolverTest {

    private WeixinMiniAppConfigurationProperties properties;
    private String stubResponse;
    private IOException stubFailure;

    /** 桩替 HTTP 外呼（覆写 protected fetchSessionResponse）。 */
    class StubResolver extends DefaultWeixinCodeResolver {
        StubResolver() {
            super(properties);
        }

        @Override
        protected String fetchSessionResponse(String jsCode) throws IOException {
            if (stubFailure != null) {
                throw stubFailure;
            }
            return stubResponse;
        }
    }

    @BeforeEach
    void setUp() {
        properties = new WeixinMiniAppConfigurationProperties();
        properties.setAppId("wx123");
        properties.setSecret("secret-abc");
        stubResponse = null;
        stubFailure = null;
    }

    private StubResolver newResolver() {
        return new StubResolver();
    }

    @Test
    void resolveUnionId_returnsUnionIdOnSuccess() {
        stubResponse = "{\"openid\":\"oAB***\",\"unionid\":\"oX8abcUnionId\",\"session_key\":\"sk\",\"errcode\":0}";
        assertEquals("oX8abcUnionId", newResolver().resolveUnionId("js-code-1"));
    }

    @Test
    void resolveUnionId_invalidCodeMappedTo20006() {
        stubResponse = "{\"errcode\":40029,\"errmsg\":\"invalid code\"}";
        CoreException e = assertThrows(MiniAppUnauthorizedException.class,
                () -> newResolver().resolveUnionId("js-code-2"));
        assertEquals(MiniAppErrorCodes.CREDENTIAL_INVALID, e.getErrorCode());
        assertTrue(e.getMessage().contains("登录凭证无效"));
    }

    @Test
    void resolveUnionId_consumedCodeMappedTo20007() {
        stubResponse = "{\"errcode\":40163,\"errmsg\":\"code been used\"}";
        CoreException e = assertThrows(MiniAppUnauthorizedException.class,
                () -> newResolver().resolveUnionId("js-code-3"));
        assertEquals(MiniAppErrorCodes.CREDENTIAL_USED, e.getErrorCode());
        assertTrue(e.getMessage().contains("已被使用"));
    }

    @Test
    void resolveUnionId_rateLimitedMappedTo20015() {
        stubResponse = "{\"errcode\":45011,\"errmsg\":\"freq limit\"}";
        CoreException e = assertThrows(MiniAppRateLimitException.class,
                () -> newResolver().resolveUnionId("js-code-4"));
        assertEquals(MiniAppErrorCodes.RATE_LIMITED, e.getErrorCode());
        assertTrue(e.getMessage().contains("频繁"));
    }

    @Test
    void resolveUnionId_otherErrcodeMappedTo20006() {
        stubResponse = "{\"errcode\":40013,\"errmsg\":\"invalid appid\"}";
        CoreException e = assertThrows(MiniAppUnauthorizedException.class,
                () -> newResolver().resolveUnionId("js-code-5"));
        assertEquals(MiniAppErrorCodes.CREDENTIAL_INVALID, e.getErrorCode());
    }

    @Test
    void resolveUnionId_missingUnionIdMappedTo20010() {
        // errcode=0 但无 unionid（小程序未绑定微信开放平台）
        stubResponse = "{\"openid\":\"oAB***\",\"session_key\":\"sk\",\"errcode\":0}";
        CoreException e = assertThrows(MiniAppUnauthorizedException.class,
                () -> newResolver().resolveUnionId("js-code-6"));
        assertEquals(MiniAppErrorCodes.IDENTITY_UNAVAILABLE, e.getErrorCode());
    }

    @Test
    void resolveUnionId_ioFailureMappedTo20006() {
        stubFailure = new IOException("connection refused");
        CoreException e = assertThrows(MiniAppUnauthorizedException.class,
                () -> newResolver().resolveUnionId("js-code-7"));
        assertEquals(MiniAppErrorCodes.CREDENTIAL_INVALID, e.getErrorCode());
    }

    // ===== [DEBUG-REMOVE-BEGIN] 联调调试 code 解析用例：随调试功能整体删除 =====

    @Test
    void debugCode_bindMode_returnsDeterministicFakeUnionId() {
        properties.setDebugEnabled(true);
        assertEquals("debug-union-vegseller", newResolver().resolveUnionId("test_vegseller_bind"));
    }

    @Test
    void debugCode_unbindMode_simulatesNotBound() {
        properties.setDebugEnabled(true);
        UnauthorizedException e = assertThrows(UnauthorizedException.class,
                () -> newResolver().resolveUnionId("test_vegseller_unbind"));
        assertEquals(20005, ((cn.geelato.lang.exception.CoreException) e).getErrorCode());
    }

    @Test
    void debugCode_ignoredWhenSwitchOff() {
        // 开关关闭（默认）：test_ 格式走正常微信解析（桩返回无效凭证响应 → 20006）
        stubResponse = "{\"errcode\":40029,\"errmsg\":\"invalid code\"}";
        CoreException e = assertThrows(MiniAppUnauthorizedException.class,
                () -> newResolver().resolveUnionId("test_vegseller_bind"));
        assertEquals(MiniAppErrorCodes.CREDENTIAL_INVALID, e.getErrorCode());
    }
    // ===== [DEBUG-REMOVE-END] =====
}
