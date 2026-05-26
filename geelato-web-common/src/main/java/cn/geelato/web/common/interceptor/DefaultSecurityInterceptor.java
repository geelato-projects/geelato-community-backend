package cn.geelato.web.common.interceptor;

import cn.geelato.core.GlobalContext;
import cn.geelato.core.env.EnvManager;
import cn.geelato.core.util.BeansUtils;
import cn.geelato.security.*;

import cn.geelato.utils.StringUtils;
import cn.geelato.web.common.online.OnlineUserTracker;
import cn.geelato.web.common.interceptor.annotation.IgnoreVerify;
import cn.geelato.web.common.oauth2.OAuth2Helper;
import cn.geelato.web.common.shiro.OAuth2Token;
import cn.geelato.web.common.shiro.WeixinUnionIdToken;
import cn.geelato.web.common.shiro.WeixinWorkUserIdToken;
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

import org.apache.shiro.authc.AuthenticationToken;

import java.util.Calendar;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class DefaultSecurityInterceptor implements HandlerInterceptor {
    private static final String __AuthorizationTag__="Authorization";
    private static final String __JWTTokenTag__="JWTBearer ";
    private static final String __OAuthTokenTag__="Bearer ";
    private static final String __AnonymousTokenTag__="Anonymous ";
    private static final String __WeixinUnionIdTokenTag__ = "WeixinUnionId ";
    private static final String __WeixinWorkUserIdTokenTag__ = "WeixinWorkUserId ";
    private static final String anonymousFixedPassword = GlobalContext.getAnonymousPwd();
    private static final long CACHE_TTL_MILLIS = 30 * 60 * 1000L;
    private final OAuthConfigurationProperties oAuthConfigurationProperties;
    private final OrgProvider orgProvider;
    private final UserProvider userProvider;

    public static final ConcurrentHashMap<String, cn.geelato.meta.User> tokenUserCache = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, UserContextCacheEntry> tokenContextCache = new ConcurrentHashMap<>();

    static {
        Thread cleanupThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(5 * 60 * 1000);
                    long now = System.currentTimeMillis();
                    tokenContextCache.entrySet().removeIf(e -> e.getValue().isExpired(now));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "SecurityInterceptor-CacheCleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    private static class UserContextCacheEntry {
        final User user;
        final Tenant tenant;
        final String password;
        final AuthenticationToken authToken;
        final long expireAt;

        UserContextCacheEntry(User user, Tenant tenant, String password, AuthenticationToken authToken) {
            this.user = user;
            this.tenant = tenant;
            this.password = password;
            this.authToken = authToken;
            this.expireAt = System.currentTimeMillis() + CACHE_TTL_MILLIS;
        }

        boolean isExpired(long now) {
            return now > expireAt;
        }
    }

    public DefaultSecurityInterceptor(OAuthConfigurationProperties config, OrgProvider orgProvider, UserProvider userProvider) {
        oAuthConfigurationProperties = config;
        this.orgProvider = orgProvider;
        this.userProvider = userProvider;
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

        if (tryRestoreFromCache(token, request)) {
            return true;
        }

        boolean authenticated = false;
        if (!authenticated) {
            authenticated = tryAnonymousAuthenticate(token, request);
        }
        if (!authenticated) {
            authenticated = tryJwtAuthenticate(token, request);
        }
        if (!authenticated) {
            authenticated = tryExtendKeyAuthenticate(token, request);
        }
        if (!authenticated) {
            authenticated = tryOAuth2Authenticate(token, request);
        }
        if (!authenticated) {
            throw new UnauthorizedException("未授权访问");
        }
        return true;
    }

    private boolean tryRestoreFromCache(String rawToken, HttpServletRequest request) {
        UserContextCacheEntry entry = tokenContextCache.get(rawToken);
        if (entry == null || entry.isExpired(System.currentTimeMillis())) {
            if (entry != null) {
                tokenContextCache.remove(rawToken, entry);
            }
            return false;
        }
        SecurityContext.setCurrentUser(entry.user);
        SecurityContext.setCurrentTenant(entry.tenant);
        SecurityContext.setCurrentPassword(entry.password);
        Subject subject = SecurityUtils.getSubject();
        subject.login(entry.authToken);
        touchOnline(entry.user, request);
        return true;
    }

    private void cacheUserContext(String rawToken, User user, String password, AuthenticationToken authToken) {
        tokenContextCache.put(rawToken, new UserContextCacheEntry(user, SecurityContext.getCurrentTenant(), password, authToken));
    }

    private boolean tryAnonymousAuthenticate(String rawToken, HttpServletRequest request) {
        String token = rawToken;
        if (!token.startsWith(__AnonymousTokenTag__)) {
            return false;
        }
        token = token.replace(__AnonymousTokenTag__, "");
        try {
            DecodedJWT verify = JWTUtil.verify(token);
            String anonymous = verify.getClaim("anonymous").asString();
            if (!StringUtils.isNotEmpty(anonymous) || !anonymousFixedPassword.equals(anonymous)) {
                return false;
            }
            String loginName = verify.getClaim("loginName").asString();
            String orgId = verify.getClaim("orgId").asString();
            String tenantCode = verify.getClaim("tenantCode").asString();
            User currentUser = EnvManager.singleInstance().InitCurrentUser(loginName, tenantCode);
            currentUser.setupOrgInfo(orgProvider);
            if (StringUtils.isNotEmpty(orgId)) {
                currentUser.setOrgId(orgId);
                currentUser.setDeptId(orgProvider.getDeptId(orgId));
                currentUser.setBuId( orgProvider.getBuId(orgId));
            }
            SecurityContext.setCurrentUser(currentUser);
            SecurityContext.setCurrentTenant(new Tenant(currentUser.getTenantCode()));
            SecurityContext.setCurrentPassword(anonymousFixedPassword);
            UsernamePasswordToken userToken = new UsernamePasswordToken(loginName, anonymousFixedPassword);
            Subject subject = SecurityUtils.getSubject();
            subject.login(userToken);
            cacheUserContext(rawToken, currentUser, anonymousFixedPassword, userToken);
            touchOnline(currentUser, request);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean tryJwtAuthenticate(String rawToken, HttpServletRequest request) {
        String token = rawToken;
        if (!token.startsWith(__JWTTokenTag__)) {
            return false;
        }
        token = token.replace(__JWTTokenTag__, "");
        try {
            DecodedJWT verify = JWTUtil.verify(token);
            String loginName = verify.getClaim("loginName").asString();
            String passWord = verify.getClaim("passWord").asString();
            String orgId = verify.getClaim("orgId").asString();
            String tenantCode = verify.getClaim("tenantCode").asString();
            if (StringUtils.isEmpty(loginName)) {
                return false;
            }
            User currentUser = EnvManager.singleInstance().InitCurrentUser(loginName, tenantCode);
            currentUser.setupOrgInfo(orgProvider);
            setupOrgInfo(currentUser, orgId);
            SecurityContext.setCurrentUser(currentUser);
            SecurityContext.setCurrentTenant(new Tenant(currentUser.getTenantCode()));
            SecurityContext.setCurrentPassword(passWord);
            UsernamePasswordToken userToken = new UsernamePasswordToken(loginName, passWord);
            Subject subject = SecurityUtils.getSubject();
            subject.login(userToken);
            cacheUserContext(rawToken, currentUser, passWord, userToken);
            touchOnline(currentUser, request);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean tryExtendKeyAuthenticate(String rawToken, HttpServletRequest request) {
        if (userProvider == null) {
            return false;
        }
        String extendKey = null;
        String extendType = null;
        Subject subject = SecurityUtils.getSubject();
        if (rawToken.startsWith(__WeixinUnionIdTokenTag__)) {
            extendKey = rawToken.replace(__WeixinUnionIdTokenTag__, "");
            extendType = "weixinUnionId";
        } else if (rawToken.startsWith(__WeixinWorkUserIdTokenTag__)) {
            extendKey = rawToken.replace(__WeixinWorkUserIdTokenTag__, "");
            extendType = "weixinWorkUserId";
        }
        if (StringUtils.isEmpty(extendKey) || StringUtils.isEmpty(extendType)) {
            return false;
        }
        try {
            User user = userProvider.getUserByExtendKey(extendKey, extendType);
            User currentUser = EnvManager.singleInstance().InitCurrentUser(user.getLoginName(), user.getTenantCode());
            if (currentUser == null || StringUtils.isEmpty(currentUser.getLoginName())) {
                return false;
            }
            currentUser.setupOrgInfo(orgProvider);
            SecurityContext.setCurrentUser(currentUser);
            SecurityContext.setCurrentTenant(new Tenant(currentUser.getTenantCode()));
            SecurityContext.setCurrentPassword(anonymousFixedPassword);
            AuthenticationToken authToken;
            if ("weixinUnionId".equals(extendType)) {
                authToken = new WeixinUnionIdToken(extendKey);
            } else {
                authToken = new WeixinWorkUserIdToken(extendKey);
            }
            subject.login(authToken);
            cacheUserContext(rawToken, currentUser, anonymousFixedPassword, authToken);
            touchOnline(currentUser, request);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean tryOAuth2Authenticate(String rawToken, HttpServletRequest request) {
        String token = rawToken;
        if (!token.startsWith(__OAuthTokenTag__)) {
            return false;
        }
        token = token.replace(__OAuthTokenTag__, "");

        UserContextCacheEntry cachedEntry = tokenContextCache.get(rawToken);
        if (cachedEntry != null && !cachedEntry.isExpired(System.currentTimeMillis())) {
            SecurityContext.setCurrentUser(cachedEntry.user);
            SecurityContext.setCurrentTenant(cachedEntry.tenant);
            SecurityContext.setCurrentPassword(cachedEntry.password);
            Subject subject = SecurityUtils.getSubject();
            subject.login(cachedEntry.authToken);
            return true;
        }
        if (cachedEntry != null) {
            tokenContextCache.remove(rawToken, cachedEntry);
        }

        cn.geelato.meta.User user = tokenUserCache.get(token);
        if (user != null) {
            User currentUser = performOAuth2Login(user, token, rawToken);
            touchOnline(currentUser, request);
            return true;
        }
        try {
            user = OAuth2Helper.getUserInfo(oAuthConfigurationProperties.getUrl(), token);
            if (user != null) {
                tokenUserCache.put(token, user);
                User currentUser = performOAuth2Login(user, token, rawToken);
                touchOnline(currentUser, request);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private void setupOrgInfo(User currentUser, String orgId) {
        if (currentUser == null) {
            return;
        }
        if (StringUtils.isNotEmpty(orgId)) {
            currentUser.setOrgId(orgId);
            currentUser.setDeptId(orgProvider.getDeptId(orgId));
            currentUser.setBuId(orgProvider.getBuId(orgId));
        }
    }

    private User resolveLocalUser(String weixinUnionId, String weixinWorkUserId) {
        if (userProvider == null) {
            return null;
        }
        User user = null;
        if (StringUtils.isNotEmpty(weixinUnionId)) {
            user = userProvider.getUserByExtendKey(weixinUnionId, "weixinUnionId");
        }
        if (user == null && StringUtils.isNotEmpty(weixinWorkUserId)) {
            user = userProvider.getUserByExtendKey(weixinWorkUserId, "weixinWorkUserId");
        }
        return user;
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
    private void performOAuth2Login(cn.geelato.meta.User user, String accessToken) {
        performOAuth2Login(user, accessToken, __OAuthTokenTag__ + accessToken);
    }

    private User performOAuth2Login(cn.geelato.meta.User user, String accessToken, String rawToken) {
        String loginName = user.getLoginName();
        User currentUser = EnvManager.singleInstance().InitCurrentUser(loginName, "geelato");
        currentUser.setupOrgInfo(orgProvider);
        SecurityContext.setCurrentUser(currentUser);
        SecurityContext.setCurrentTenant(new Tenant(user.getTenantCode()));
        OAuth2Token oauth2Token = new OAuth2Token(accessToken);
        Subject subject = SecurityUtils.getSubject();
        subject.login(oauth2Token);
        cacheUserContext(rawToken, currentUser, anonymousFixedPassword, oauth2Token);
        return currentUser;
    }

    private void touchOnline(User user, HttpServletRequest request) {
        if (user == null || request == null) {
            return;
        }
        try {
            OnlineUserTracker tracker = BeansUtils.getBean(OnlineUserTracker.class);
            if (tracker != null) {
                tracker.touch(user, request);
            }
        } catch (Exception ignored) {
        }
    }
}
