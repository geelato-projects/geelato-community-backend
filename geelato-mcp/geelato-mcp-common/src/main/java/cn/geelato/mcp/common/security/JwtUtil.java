package cn.geelato.mcp.common.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JwtUtil {

    private static final Integer DEFAULT_TOKEN_SIZE = 3;

    public static DecodedJWT verify(String token, String signKey) throws Exception {
        if (token == null || token.isEmpty()) {
            throw new JWTDecodeException("无效的token！");
        }
        String dToken = deConfoundPayload(token);
        return JWT.require(Algorithm.HMAC256(signKey)).build().verify(dToken);
    }

    private static String deConfoundPayload(String token) throws Exception {
        String[] split = token.split("\\.");
        if (split.length != DEFAULT_TOKEN_SIZE) {
            throw new JWTDecodeException("签名不正确");
        }
        String payload = split[1];
        return split[0] + "." + reversePayload(payload, payload.length() / 2) + "." + split[2];
    }

    private static String reversePayload(String payload, Integer index) {
        return payload.substring(index) + payload.substring(0, index);
    }
}
