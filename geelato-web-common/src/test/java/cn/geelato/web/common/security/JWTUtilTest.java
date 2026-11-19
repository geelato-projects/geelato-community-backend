package cn.geelato.web.common.security;

import cn.geelato.core.GlobalContext;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 平台自签 JWT 的签发/校验往返语义（环境无关）：
 * 签名密钥取自 GlobalContext.getJwtSignKey()（env 优先、默认值兜底），
 * 此处不断言具体密钥值，仅验证 sign/verify 使用同一来源且相互配套。
 */
class JWTUtilTest {

    private Map<String, String> claims() {
        Map<String, String> map = new HashMap<>();
        map.put("loginName", "cs_demo");
        map.put("orgId", "123456");
        return map;
    }

    @Test
    void signThenVerifyRoundTrip() throws Exception {
        String token = JWTUtil.getToken(claims(), 60);
        DecodedJWT jwt = JWTUtil.verify(token);
        assertEquals("cs_demo", jwt.getClaim("loginName").asString());
        assertEquals("123456", jwt.getClaim("orgId").asString());
    }

    @Test
    void defaultExpiryOverloadAlsoVerifiable() throws Exception {
        String token = JWTUtil.getToken(claims());
        DecodedJWT jwt = JWTUtil.verify(token);
        assertEquals("cs_demo", jwt.getClaim("loginName").asString());
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = JWTUtil.getToken(claims(), 60);
        // payload 混淆后仍是三段式；替换中段任一字符即破坏验签
        String[] parts = token.split("\\.");
        StringBuilder tamperedPayload = new StringBuilder(parts[1]);
        int mid = tamperedPayload.length() / 2;
        char origin = tamperedPayload.charAt(mid);
        char flipped = origin == 'A' ? 'B' : 'A';
        tamperedPayload.setCharAt(mid, flipped);
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];
        assertThrows(Exception.class, () -> JWTUtil.verify(tampered));
    }

    @Test
    void invalidTokenIsRejected() {
        assertThrows(JWTDecodeException.class, () -> JWTUtil.verify(""));
        assertThrows(Exception.class, () -> JWTUtil.verify("not-a-jwt"));
    }

    @Test
    void signKeyIsPresent() {
        String key = GlobalContext.getJwtSignKey();
        assertNotNull(key);
        // 环境变量 GEELATO_JWT_SIGN_KEY 未注入时应兜底为内置默认值；注入时随 env
        String expected = System.getenv("GEELATO_JWT_SIGN_KEY");
        if (expected == null || expected.trim().isEmpty()) {
            assertTrue(key.length() >= 16);
        } else {
            assertEquals(expected.trim(), key);
        }
    }

    @Test
    void anonymousPwdIsPresent() {
        // 不断言具体值：env 未注入时为内置默认，注入时随 env
        String env = System.getenv("GEELATO_ANONYMOUS_PWD");
        String pwd = GlobalContext.getAnonymousPwd();
        assertNotNull(pwd);
        assertFalse(pwd.isEmpty());
        if (env != null && !env.trim().isEmpty()) {
            assertEquals(env.trim(), pwd);
        }
    }
}
