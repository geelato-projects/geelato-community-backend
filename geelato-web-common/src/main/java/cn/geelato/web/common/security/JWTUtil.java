package cn.geelato.web.common.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Calendar;
import java.util.Map;

/**
 * 平台自签 JWT（HMAC256 + payload 前后半段对调混淆）的唯一实现。
 * 签发（JWTAuthController 等）与校验（JwtTokenAuthenticator）共用本类，
 * 原拦截器嵌套版与 cn.geelato.web.platform.utils 版为同源双拷贝，已合并于此。
 */
public class JWTUtil {

    /**
     * 签名 此签名为 16位 大写 MD5
     */
    private static final String SIGN_KEY = "5A1332068BA9FD17";

    /**
     * 默认的过期时间，43200分钟
     */
    private static final Integer DEFAULT_EXPIRES = 60 * 43200;

    /**
     * token默认的长度
     */
    private static final Integer DEFAULT_TOKEN_SIZE = 3;

    private JWTUtil() {
    }

    /**
     * 生成令牌
     *
     * @param map     数据正文
     * @param expires 过期时间，单位(秒)
     */
    public static String getToken(Map<String, String> map, Integer expires) throws Exception {
        Calendar instance = Calendar.getInstance();
        instance.add(Calendar.SECOND, expires);
        JWTCreator.Builder builder = JWT.create();
        map.forEach(builder::withClaim);
        String token = builder.withExpiresAt(instance.getTime())
                .sign(Algorithm.HMAC256(SIGN_KEY));
        return confoundPayload(token);
    }

    /**
     * 解析token
     *
     * @param token 输入混淆payload后的token
     */
    public static DecodedJWT verify(String token) throws Exception {
        //如果token无效
        if (token == null || token.isEmpty()) {
            throw new JWTDecodeException("无效的token！");
        }
        //解析token
        String dToken = deConfoundPayload(token);
        //创建返回结果
        return JWT.require(Algorithm.HMAC256(SIGN_KEY)).build().verify(dToken);
    }

    /**
     * 重载getToken 此方法为获取默认30分钟有效期的token
     *
     * @param map 数据正文
     */
    public static String getToken(Map<String, String> map) throws Exception {
        return getToken(map, DEFAULT_EXPIRES);
    }


    /**
     * 对一个base64编码进行混淆  此处还可以进行replace混淆，考虑到效率问题，这里就不做啦~
     * 对于加密的思路还有位移、字符替换等~
     *
     * @param token 混淆payload前的token
     */
    private static String confoundPayload(String token) throws Exception {
        String[] split = token.split("\\.");
        if (split.length != DEFAULT_TOKEN_SIZE) {
            throw new JWTDecodeException("签名不正确");
        }
        String payload = split[1];
        int length = payload.length() / 2;
        int index = payload.length() % 2 != 0 ? length + 1 : length;
        return split[0] + "." + reversePayload(payload, index) + "." + split[2];
    }

    /**
     * 对一个混淆后的base编码进行解析
     *
     * @param token 混淆后的token
     */
    private static String deConfoundPayload(String token) throws Exception {
        String[] split = token.split("\\.");
        if (split.length != DEFAULT_TOKEN_SIZE) {
            throw new JWTDecodeException("签名不正确");
        }
        String payload = split[1];
        return split[0] + "." + reversePayload(payload, payload.length() / 2) + "." + split[2];
    }

    /**
     * 将md5编码位移
     *
     * @param payload payload编码
     * @param index   位移处
     */
    private static String reversePayload(String payload, Integer index) {
        return payload.substring(index) + payload.substring(0, index);
    }

}
