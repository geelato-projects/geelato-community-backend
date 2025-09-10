package cn.geelato.web.common.interceptor;

import cn.geelato.core.env.EnvManager;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;

import cn.geelato.utils.StringUtils;
import cn.geelato.web.common.interceptor.annotation.IgnoreVerify;
import cn.geelato.web.common.oauth2.OAuth2Helper;
import cn.geelato.web.common.oauth2.OAuth2ServerTokenResult;
import cn.geelato.web.common.oauth2.TokenManager;
import cn.geelato.web.common.shiro.OAuth2Token;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Calendar;
import java.util.Map;
@Slf4j
public class DefaultSecurityInterceptor implements HandlerInterceptor {
    private static final String __AuthorizationTag__="Authorization";
    private static final String __JWTTokenTag__="JWTBearer ";
    private static final String __OAuthTokenTag__="Bearer ";
    private final OAuthConfigurationProperties oAuthConfigurationProperties;

    public DefaultSecurityInterceptor(OAuthConfigurationProperties config) {
        oAuthConfigurationProperties = config;
    }

    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)
        ||handlerMethod.getMethod().isAnnotationPresent(IgnoreVerify.class)) {
            return true;
        }

        String token = request.getHeader(__AuthorizationTag__);
        if (token == null) {
            throw new UnauthorizedException();
        }
        log.info("handle token:{}",token);
        if (token.startsWith(__JWTTokenTag__)) {
            token = token.replace(__JWTTokenTag__, "");
            try {
                DecodedJWT verify = JWTUtil.verify(token);
                String loginName = verify.getClaim("loginName").asString();
                String passWord = verify.getClaim("passWord").asString();
                String orgId = verify.getClaim("orgId").asString();
                String tenantCode = verify.getClaim("tenantCode").asString();

                log.info("jwt token resolve loginName:{},passWord:{},orgId:{},tenantCode:{}", loginName, passWord, orgId, tenantCode);
                User currentUser = EnvManager.singleInstance().InitCurrentUser(loginName, tenantCode);
                if (StringUtils.isNotEmpty(orgId)) {
                    currentUser.setOrgId(orgId);
                }
                SecurityContext.setCurrentUser(currentUser);
                SecurityContext.setCurrentTenant(new Tenant(currentUser.getTenantCode()));
                SecurityContext.setCurrentPassword(passWord);
                UsernamePasswordToken userToken = new UsernamePasswordToken(loginName, passWord);
                Subject subject = SecurityUtils.getSubject();
                subject.login(userToken);
            } catch (Exception e) {
                throw new UnauthorizedException();
            }
        } else if (token.startsWith(__OAuthTokenTag__)) {
            token = token.replace(__OAuthTokenTag__, "");
            try {
                cn.geelato.web.common.security.User user = OAuth2Helper.getUserInfo(oAuthConfigurationProperties.getUrl(), token);
                if (user != null) {
                    performOAuth2Login(user, token);
                } else {
                    throw new UnauthorizedException("获取用户信息失败");
                }
            }catch (Exception e) {
                // 尝试使用refresh_token刷新访问令牌
                try {
                    String refreshToken = TokenManager.getRefreshToken(token);
                    log.info("oauth2 get user fail and refresh token，the refresh token :{}", refreshToken);
                    if (refreshToken != null) {
                        OAuth2ServerTokenResult refreshResult = OAuth2Helper.refreshToken(
                            oAuthConfigurationProperties.getUrl(),
                            oAuthConfigurationProperties.getClientId(),
                            oAuthConfigurationProperties.getClientSecret(),
                            refreshToken
                        );
                        
                        if (refreshResult != null && "200".equals(refreshResult.getCode())) {
                            // 更新token映射关系
                            TokenManager.updateTokens(token, refreshResult.getAccess_token(), refreshResult.getRefresh_token());
                            log.info("token by refresh token  :{}", refreshResult.getAccess_token());
                            // 使用新的access_token重新获取用户信息
                            cn.geelato.web.common.security.User user = OAuth2Helper.getUserInfo(
                                oAuthConfigurationProperties.getUrl(), 
                                refreshResult.getAccess_token()
                            );
                            
                            if (user != null) {
                                performOAuth2Login(user, refreshResult.getAccess_token());
                                return true;
                            }
                        }
                    }
                } catch (Exception refreshException) {
                    // 刷新token失败，移除无效的token映射
                    TokenManager.removeTokens(token);
                }
                throw new UnauthorizedException("OAuth认证失败");
            }
        } else {
            throw new UnauthorizedException("无效的令牌格式");
        }
        return true;
    }

    /**
     * @author geemeta
     */
    public static class JWTUtil {

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
            map.forEach(builder::withClaim);

            //指定过期时间
            String token = builder.withExpiresAt(instance.getTime())
                    //设置加密方式
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

    /**
     * 执行OAuth2登录操作
     * @param user OAuth2用户信息
     * @param accessToken 访问令牌
     */
    private void performOAuth2Login(cn.geelato.web.common.security.User user, String accessToken) {
        String loginName = user.getLoginName();
        User currentUser = EnvManager.singleInstance().InitCurrentUser(loginName, "geelato");
        SecurityContext.setCurrentUser(currentUser);
        SecurityContext.setCurrentTenant(new Tenant(user.getTenantCode()));
        OAuth2Token oauth2Token = new OAuth2Token(accessToken);
        Subject subject = SecurityUtils.getSubject();
        subject.login(oauth2Token);
    }
}
