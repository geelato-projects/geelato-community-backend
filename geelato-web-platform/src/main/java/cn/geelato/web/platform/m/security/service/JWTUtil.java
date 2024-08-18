package cn.geelato.web.platform.m.security.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Calendar;
import java.util.Map;
/**
 * @author geemeta
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


    /**
     * 生成令牌
     *
     * @param map     数据正文
     * @param expires 过期时间，单位(秒)
     */
    public static String getToken(Map<String, String> map, Integer expires) throws Exception {

        //创建日历
        Calendar instance = Calendar.getInstance();
        //设置过期时间
        instance.add(Calendar.SECOND, expires);

        //创建jwt builder对象
        JWTCreator.Builder builder = JWT.create();

        //payload
        map.forEach((k, v) -> {
            builder.withClaim(k, v);
        });

        //指定过期时间
        String token = builder.withExpiresAt(instance.getTime())
                //设置加密方式
                .sign(Algorithm.HMAC256(SIGN_KEY));
        //返回tokean
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
        //分割token
        String[] split = token.split("\\.");
        //如果token不符合规范
        if (split.length != DEFAULT_TOKEN_SIZE) {
            throw new JWTDecodeException("签名不正确");
        }
        //取出payload
        String payload = split[1];
        //获取长度
        int length = payload.length() / 2;
        //指定截取点
        int index = payload.length() % 2 != 0 ? length + 1 : length;
        //混淆处理后的token
        return split[0] + "." + reversePayload(payload, index) + "." + split[2];
    }

    /**
     * 对一个混淆后的base编码进行解析
     *
     * @param token 混淆后的token
     */
    private static String deConfoundPayload(String token) throws Exception {
        //分割token
        String[] split = token.split("\\.");
        //如果token不符合规范
        if (split.length != DEFAULT_TOKEN_SIZE) {
            throw new JWTDecodeException("签名不正确");
        }
        //取出payload
        String payload = split[1];
        //返回解析后的token
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