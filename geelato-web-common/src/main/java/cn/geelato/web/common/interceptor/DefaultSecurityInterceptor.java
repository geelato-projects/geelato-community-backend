package cn.geelato.web.common.interceptor;

import cn.geelato.core.GlobalContext;
import cn.geelato.core.env.EnvManager;
import cn.geelato.logging.LogContext;
import cn.geelato.security.OrgProvider;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import cn.geelato.utils.LocalBoundedCache;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.common.online.OnlineUserTracker;
import cn.geelato.web.common.interceptor.annotation.IgnoreVerify;
import cn.geelato.web.common.security.delegate.DelegateSession;
import cn.geelato.web.common.security.delegate.DelegateSessionStore;
import cn.geelato.web.common.security.spi.LoginCredential;
import cn.geelato.web.common.security.spi.TokenAuthentication;
import cn.geelato.web.common.security.spi.TokenAuthenticationContext;
import cn.geelato.web.common.security.spi.TokenAuthenticator;
import cn.geelato.web.common.security.spi.support.TokenAuthenticatorRegistry;
import cn.geelato.web.common.shiro.OAuth2Token;
import cn.geelato.web.common.shiro.SystemTokenToken;
import cn.geelato.web.common.shiro.WeixinUnionIdToken;
import cn.geelato.web.common.shiro.WeixinWorkUserIdToken;
import cn.geelato.web.common.traffic.TrafficColoringProperties;
import cn.geelato.web.common.traffic.TrafficTagContext;
import cn.geelato.web.common.traffic.TrafficTagResolver;
import cn.geelato.traffic.TrafficTagStrategy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.LinkedHashMap;

/**
 * 安全拦截器：按 Authorization 头前缀经 {@link TokenAuthenticatorRegistry} 路由到
 * 对应 {@link TokenAuthenticator} SPI 实现完成「凭证校验 + 用户解析」，本类统一负责
 * SecurityContext 填充、Shiro 会话建立、上下文缓存、委托代办、流量染色与在线打点。
 * 新增认证方式只需实现 {@link TokenAuthenticator} 并注册为 Spring Bean，无需改动本类。
 */
@Slf4j
public class DefaultSecurityInterceptor implements HandlerInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String ANONYMOUS_FIXED_PASSWORD = GlobalContext.getAnonymousPwd();
    private static final long CONTEXT_CACHE_TTL_MILLIS = 30 * 60 * 1000L;

    private static final LocalBoundedCache<String, UserContextCacheEntry> tokenContextCache =
            new LocalBoundedCache<>("token-context", CONTEXT_CACHE_TTL_MILLIS, 100_000);

    private record UserContextCacheEntry(User user, Tenant tenant, String password, AuthenticationToken authToken) {
    }

    private final OrgProvider orgProvider;
    private final TokenAuthenticatorRegistry registry;

    @Setter
    private OnlineUserTracker onlineUserTracker;
    @Setter
    private DelegateSessionStore delegateSessionStore;
    private TrafficColoringProperties trafficColoringProperties;
    private TrafficTagStrategy trafficTagStrategy;
    private TrafficTagResolver trafficTagResolver;

    public DefaultSecurityInterceptor(OrgProvider orgProvider, TokenAuthenticatorRegistry registry) {
        this.orgProvider = orgProvider;
        this.registry = registry;
    }

    public void setTrafficColoringProperties(TrafficColoringProperties trafficColoringProperties) {
        this.trafficColoringProperties = trafficColoringProperties;
        rebuildTrafficTagResolver();
    }

    public void setTrafficTagStrategy(TrafficTagStrategy trafficTagStrategy) {
        this.trafficTagStrategy = trafficTagStrategy;
        rebuildTrafficTagResolver();
    }

    // ========== 认证主流程 ==========

    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) {
        applyTrafficTag(request, response);
        if (!(handler instanceof HandlerMethod handlerMethod)
                || handlerMethod.getMethod().isAnnotationPresent(IgnoreVerify.class)) {
            return true;
        }

        String token = request.getHeader(AUTHORIZATION_HEADER);
        if (token == null) {
            log.warn("unauthorized request without authorization header, method:{}, url:{}",
                    request.getMethod(), buildRequestUrl(request));
            throw new UnauthorizedException();
        }
        log.info("handle token scheme:{}", schemeOf(token));

        TokenAuthenticator authenticator = registry.resolve(token);
        TokenAuthenticationContext authContext = new TokenAuthenticationContext(token, request, handlerMethod);
        // 方法级门禁（如 SystemToken 的 @AllowSystemAccess）先于缓存还原，防止借缓存绕过
        authenticator.preAuthCheck(authContext);
        if (tryRestoreFromCache(token, request, response)) {
            return true;
        }

        TokenAuthentication result = authenticator.authenticate(authContext);
        establishSession(token, result, request, response);
        applyDelegation(token);
        return true;
    }

    @Override
    public void afterCompletion(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler, Exception ex) {
        try {
            if (trafficColoringProperties != null) {
                LogContext.remove(trafficColoringProperties.getMdcKey());
            }
        } catch (Exception ignored) {
        }
        try {
            TrafficTagContext.clear();
        } catch (Exception ignored) {
        }
    }

    /**
     * 统一会话建立：SecurityContext 填充 → Shiro 登录（按凭证类型路由既有 Realm）
     * → 上下文缓存 → 认证后流量染色 → 在线打点。认证器不再各自重复此流程。
     */
    private void establishSession(String rawToken, TokenAuthentication result,
                                  HttpServletRequest request, HttpServletResponse response) {
        User user = result.user();
        SecurityContext.setCurrentUser(user);
        SecurityContext.setCurrentTenant(new Tenant(user.getTenantCode()));
        SecurityContext.setCurrentPassword(result.password());
        AuthenticationToken authToken = createShiroToken(result.login());
        try {
            SecurityUtils.getSubject().login(authToken);
        } catch (AuthenticationException e) {
            log.warn("Shiro login failed, scheme:{}, principal:{}, url:{}, error:{}",
                    result.login().type(), result.login().principal(), buildRequestUrl(request), e.getMessage());
            throw new UnauthorizedException("认证失败: " + e.getMessage());
        }
        cacheUserContext(rawToken, user, result.password(), authToken);
        applyTrafficTagAfterAuthenticated(user, request, response);
        if (result.trackOnline()) {
            touchOnline(user, request);
        }
    }

    /** 凭证类型 → 既有 Shiro Token/Realm 路由。未知类型属装配错误，硬失败。 */
    private AuthenticationToken createShiroToken(LoginCredential login) {
        return switch (login.type()) {
            case LoginCredential.TYPE_USERNAME_PASSWORD ->
                    new UsernamePasswordToken(login.principal(), login.credential());
            case LoginCredential.TYPE_WEIXIN_UNION_ID -> new WeixinUnionIdToken(login.principal());
            case LoginCredential.TYPE_WEIXIN_WORK_USER_ID -> new WeixinWorkUserIdToken(login.principal());
            case LoginCredential.TYPE_OAUTH2 -> new OAuth2Token(login.principal());
            case LoginCredential.TYPE_SYSTEM_TOKEN -> new SystemTokenToken(login.principal());
            default -> throw new IllegalStateException("未支持的登录凭证类型: " + login.type()
                    + "，请扩展 DefaultSecurityInterceptor#createShiroToken");
        };
    }

    private String schemeOf(String token) {
        int spaceIdx = token.indexOf(' ');
        return spaceIdx > 0 ? token.substring(0, spaceIdx) : "unknown";
    }

    // ========== 上下文缓存（token → 已建立会话的还原） ==========

    private boolean tryRestoreFromCache(String rawToken, HttpServletRequest request, HttpServletResponse response) {
        UserContextCacheEntry entry = tokenContextCache.get(rawToken);
        if (entry == null) {
            return false;
        }
        SecurityContext.setCurrentUser(entry.user);
        SecurityContext.setCurrentTenant(entry.tenant);
        SecurityContext.setCurrentPassword(entry.password);
        try {
            SecurityUtils.getSubject().login(entry.authToken);
        } catch (AuthenticationException e) {
            // 缓存的凭证已失效（如用户修改密码后，基于旧密码生成的令牌无法通过校验）。
            // 清理失效缓存并落回全量认证链，避免向外泄漏 Shiro 原始异常。
            tokenContextCache.remove(rawToken);
            log.warn("cached credential invalidated, maybe password changed, url:{}", buildRequestUrl(request));
            return false;
        }
        applyTrafficTagAfterAuthenticated(entry.user, request, response);
        touchOnline(entry.user, request);
        return true;
    }

    private void cacheUserContext(String rawToken, User user, String password, AuthenticationToken authToken) {
        tokenContextCache.put(rawToken, new UserContextCacheEntry(user, SecurityContext.getCurrentTenant(), password, authToken));
    }

    /**
     * 失效指定凭证 key 的上下文缓存。供委托代办切换/退出接口调用，
     * 确保下一次请求重新认证并应用最新的委托态（而非被旧缓存还原成原身份）。
     */
    public static void invalidateTokenContextCache(String rawToken) {
        if (rawToken == null || rawToken.isEmpty()) {
            return;
        }
        tokenContextCache.remove(rawToken);
    }

    // ========== 委托代办（token 不变，服务端换身份） ==========

    /**
     * 应用委托代办态：若当前凭证存在委托代办会话，则用目标身份（委托人）重新加载
     * SecurityContext，并注入实际操作人（代理人）到
     * {@code User.delegateUserId/delegateUserName} 供审计使用。
     * <p>
     * token 全程不变，对业务模块透明；JWT 与 OAuth2 走同一条逻辑。
     * <p>
     * 注意：委托会话写入/移除时已通过 {@link #invalidateTokenContextCache(String)} 清除旧上下文缓存，
     * 避免 tryRestoreFromCache 还原成原身份。此处命中后再 cacheUserContext，使后续请求命中缓存。
     */
    private void applyDelegation(String rawToken) {
        if (delegateSessionStore == null || rawToken == null || rawToken.isEmpty()) {
            return;
        }
        DelegateSession session = delegateSessionStore.get(rawToken);
        if (session == null) {
            return;
        }
        try {
            User actUser = copyOf(EnvManager.singleInstance().InitCurrentUser(session.getTargetLoginName(), session.getTenantCode()));
            if (actUser == null) {
                log.warn("applyDelegation: target user not found, loginName:{}, fallback to origin identity",
                        session.getTargetLoginName());
                return;
            }
            actUser.setupOrgInfo(orgProvider);
            actUser.setDelegateUserId(session.getOriginUserId());
            actUser.setDelegateUserName(session.getOriginUserName() != null
                    ? session.getOriginUserName() : session.getOriginLoginName());
            SecurityContext.setCurrentUser(actUser);
            SecurityContext.setCurrentTenant(new Tenant(session.getTenantCode()));
            cacheUserContext(rawToken, actUser, SecurityContext.getCurrentPassword(), buildShiroToken(actUser));
        } catch (Exception e) {
            log.warn("applyDelegation failed, fallback to origin identity, target:{}, err:{}",
                    session.getTargetLoginName(), e.getMessage());
        }
    }

    private AuthenticationToken buildShiroToken(User user) {
        String password = SecurityContext.getCurrentPassword();
        if (password == null || password.isEmpty()) {
            password = ANONYMOUS_FIXED_PASSWORD;
        }
        return new UsernamePasswordToken(user.getLoginName(), password);
    }

    /** EnvManager 缓存的是共享实例，切换身份等修改前必须先克隆，防止多 token 互相覆盖。 */
    private User copyOf(User source) {
        if (source == null) {
            return null;
        }
        User copy = new User();
        BeanUtils.copyProperties(source, copy);
        // BeanUtils 对 Map 只拷引用：隔离 extendInfo，防止会话副本变更污染共享快照
        if (source.getExtendInfo() != null) {
            copy.setExtendInfo(new LinkedHashMap<>(source.getExtendInfo()));
        }
        return copy;
    }

    // ========== 流量染色 ==========

    private void applyTrafficTag(HttpServletRequest request, HttpServletResponse response) {
        if (trafficTagResolver == null) {
            return;
        }
        try {
            trafficTagResolver.resolveAndApply(request, response);
        } catch (Exception e) {
            log.debug("apply traffic tag failed", e);
        }
    }

    private void applyTrafficTagAfterAuthenticated(User user, HttpServletRequest request, HttpServletResponse response) {
        if (trafficTagResolver == null || user == null) {
            return;
        }
        try {
            trafficTagResolver.applyAfterAuthenticated(user, request, response);
        } catch (Exception e) {
            log.debug("apply traffic tag after authenticated failed", e);
        }
    }

    private void rebuildTrafficTagResolver() {
        this.trafficTagResolver = trafficColoringProperties == null
                ? null : new TrafficTagResolver(trafficColoringProperties, trafficTagStrategy);
    }

    // ========== 辅助 ==========

    private void touchOnline(User user, HttpServletRequest request) {
        if (onlineUserTracker == null || user == null || request == null) {
            return;
        }
        try {
            onlineUserTracker.touch(user, request);
        } catch (Exception ignored) {
        }
    }

    private String buildRequestUrl(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String url = request.getRequestURL().toString();
        String queryString = request.getQueryString();
        return StringUtils.isNotEmpty(queryString) ? url + "?" + queryString : url;
    }
}
