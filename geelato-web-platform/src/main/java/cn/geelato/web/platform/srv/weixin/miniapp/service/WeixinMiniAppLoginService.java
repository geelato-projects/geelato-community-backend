package cn.geelato.web.platform.srv.weixin.miniapp.service;

import cn.geelato.core.GlobalContext;
import cn.geelato.meta.User;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.query.Filter;
import cn.geelato.security.SecurityDataRefreshCoordinator;
import cn.geelato.web.common.security.JWTUtil;
import cn.geelato.web.common.security.spi.WeixinCodeResolver;
import cn.geelato.web.platform.boot.properties.WeixinMiniAppConfigurationProperties;
import cn.geelato.web.platform.srv.auth.service.CurrentUserProfileService;
import cn.geelato.web.platform.srv.security.entity.LoginResult;
import cn.geelato.web.platform.srv.security.service.UserService;
import cn.geelato.web.platform.srv.weixin.miniapp.MiniAppAccountDisabledException;
import cn.geelato.web.platform.srv.weixin.miniapp.MiniAppBindConflictException;
import cn.geelato.web.platform.srv.weixin.miniapp.MiniAppErrorCodes;
import cn.geelato.web.platform.srv.weixin.miniapp.MiniAppUnauthorizedException;
import cn.geelato.web.platform.srv.weixin.miniapp.DebugLoginCode;
import cn.geelato.web.platform.srv.weixin.miniapp.entity.MiniAppAccount;
import cn.geelato.web.platform.srv.weixin.miniapp.entity.MiniAppBoundEvent;
import cn.geelato.web.platform.srv.weixin.miniapp.entity.MiniAppLoginResult;
import cn.geelato.web.platform.srv.weixin.miniapp.entity.MiniAppPreBindAccount;
import cn.geelato.web.platform.srv.weixin.miniapp.entity.MiniAppPreBindResult;
import cn.geelato.web.platform.srv.weixin.miniapp.entity.MiniAppUnboundEvent;
import cn.geelato.web.platform.srv.weixin.miniapp.spi.MiniAppAccountResolver;
import cn.geelato.web.platform.srv.weixin.miniapp.spi.MiniAppLoginResultEnhancer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 微信小程序登录/绑定核心编排：login（unionid 反查静默登录）、pre-bind（手机号匹配 + 票据签发）、
 * bind（验票 + 严格一对一查重 + 写 weixin_unionId）、unbind（清绑定）。
 * <p>
 * 绑定存储操作沿用 WeChatController 同型（UserService.queryModel 查重 / updateModel 写清，
 * del_status 由查询基线自动过滤）；token 签发与 /api/user/login（JWTAuthController）同一套规范
 * （JWTUtil 同款 claim：id/loginName/passWord/orgId/tenantCode，passWord 用平台匿名固定密码）。
 * 角色/客户名等业务字段由 {@link MiniAppLoginResultEnhancer} SPI 填充，平台默认实现留空。
 */
@Slf4j
@Service
public class WeixinMiniAppLoginService {

    private static final int ENABLE_STATUS_ENABLED = 1;

    private final UserService userService;
    private final WeixinCodeResolver weixinCodeResolver;
    private final MiniAppAccountResolver accountResolver;
    private final MiniAppLoginResultEnhancer loginResultEnhancer;
    private final MiniAppTicketService ticketService;
    private final WeixinMiniAppApiClient apiClient;
    private final WeixinMiniAppConfigurationProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final CurrentUserProfileService currentUserProfileService;
    /** 用户快照刷新协调器（可缺席：直连式实现自行管理数据面时）；bind/unbind 后刷新使 unionid extend 索引即时生效 */
    private final SecurityDataRefreshCoordinator refreshCoordinator;

    public WeixinMiniAppLoginService(UserService userService,
                                     WeixinCodeResolver weixinCodeResolver,
                                     MiniAppAccountResolver accountResolver,
                                     MiniAppLoginResultEnhancer loginResultEnhancer,
                                     MiniAppTicketService ticketService,
                                     WeixinMiniAppApiClient apiClient,
                                     WeixinMiniAppConfigurationProperties properties,
                                     ApplicationEventPublisher eventPublisher,
                                     CurrentUserProfileService currentUserProfileService,
                                     @Autowired(required = false) SecurityDataRefreshCoordinator refreshCoordinator) {
        this.userService = userService;
        this.weixinCodeResolver = weixinCodeResolver;
        this.accountResolver = accountResolver;
        this.loginResultEnhancer = loginResultEnhancer;
        this.ticketService = ticketService;
        this.apiClient = apiClient;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.currentUserProfileService = currentUserProfileService;
        this.refreshCoordinator = refreshCoordinator;
    }

    /** 静默登录：unionid 反查绑定账号，命中即返回身份（内含 token）；未绑定抛 20011。 */
    public MiniAppLoginResult login(String code) {
        // ===== [DEBUG-REMOVE-BEGIN] 联调调试通道（debug-enabled=true 时生效）：test_{账号}_bind 直接以该账号登录；test_{账号}_unbind 模拟未绑定。未来整体删除本段 =====
        if (properties.isDebugEnabled()) {
            Optional<DebugLoginCode.DebugCode> debug = DebugLoginCode.parse(code);
            if (debug.isPresent()) {
                DebugLoginCode.DebugCode dc = debug.get();
                if (dc.mode() == DebugLoginCode.Mode.UNBIND) {
                    log.info("[调试] 模拟未绑定场景, account={}", dc.account());
                    throw new MiniAppUnauthorizedException(MiniAppErrorCodes.NOT_BOUND, "请先绑定账号");
                }
                log.warn("[调试] 跳过微信认证, 以账号 {} 直接登录（debug-enabled=true，生产严禁开启）", dc.account());
                return buildLoginResult(findDebugUserByLoginName(dc.account()));
            }
        }
        // ===== [DEBUG-REMOVE-END] =====
        String unionId = resolveUnionId(code);
        User user = findBoundUser(unionId);
        return buildLoginResult(user);
    }

    /** 手机号预匹配：匹配到已开通账号则返回脱敏信息与一次性绑定票据。 */
    public MiniAppPreBindResult preBind(String phoneCode) {
        String phone = apiClient.getPhoneNumber(phoneCode);
        MiniAppPreBindResult result = new MiniAppPreBindResult();
        MiniAppAccount account = accountResolver.resolve(phone).orElse(null);
        if (account == null) {
            result.setMatched(false);
            return result;
        }
        result.setMatched(true);
        MiniAppPreBindAccount view = new MiniAppPreBindAccount();
        view.setUserId(account.getUserId());
        view.setLoginName(account.getLoginName());
        view.setUserName(maskName(account.getUserName()));
        view.setOrgName(account.getOrgName());
        view.setMobile(maskMobile(phone));
        result.setAccount(view);
        result.setPreBindTicket(ticketService.issue(account.getUserId(), phone));
        return result;
    }

    /**
     * 确认绑定：验票 → 解析微信身份 → 严格一对一双向查重 → 写 weixin_unionId → 发事件。
     * 目标账号以票据内 userId 为准（bind 不收客户端账号参数，防越权绑定）。
     */
    public MiniAppLoginResult bind(String code, String preBindTicket) {
        // 验票（签名/有效期/一次性），payload 拆解：userId|phoneDigest|expireAt|nonce
        MiniAppTicketService.VerifiedTicket verified = ticketService.verifyAndConsume(preBindTicket);
        String targetUserId = verified.userId();
        // ===== [DEBUG-REMOVE-BEGIN] 联调调试：debug code 构造假 unionid（debug-union-{账号}）。未来删除本行并改回：String unionId = resolveUnionId(code); =====
        String unionId = debugUnionIdOrResolve(code);
        // ===== [DEBUG-REMOVE-END] =====

        User target = userService.getModel(User.class, targetUserId);
        if (target == null) {
            // 票据签发后账号被删：等同票据失效
            throw new MiniAppUnauthorizedException(MiniAppErrorCodes.TICKET_INVALID, "绑定确认已过期，请重新发起绑定");
        }
        if (target.getEnableStatus() != ENABLE_STATUS_ENABLED) {
            throw new MiniAppAccountDisabledException();
        }
        // 手机号摘要一致性校验：票据签发后账号手机号已变更 → 票据失效（重新 pre-bind）
        if (!ticketService.phoneMatches(verified.phoneDigest(), target.getMobilePhone())) {
            log.warn("bind 手机号摘要与账号当前手机号不一致, userId={}", targetUserId);
            throw new MiniAppUnauthorizedException(MiniAppErrorCodes.TICKET_INVALID, "绑定确认已过期，请重新发起绑定");
        }
        // 双向严格一对一查重
        List<User> occupied = userService.queryModel(User.class, Map.of("weixinUnionId", unionId));
        if (occupied != null && occupied.stream().anyMatch(u -> !targetUserId.equals(u.getId()))) {
            throw new MiniAppBindConflictException(MiniAppErrorCodes.UNION_ID_OCCUPIED, "该微信已绑定其他账号，请先解绑");
        }
        if (target.getWeixinUnionId() != null && !target.getWeixinUnionId().isEmpty()
                && !target.getWeixinUnionId().equals(unionId)) {
            throw new MiniAppBindConflictException(MiniAppErrorCodes.ACCOUNT_ALREADY_BOUND, "该账号已绑定其他微信，请先解绑");
        }

        // 精确列更新（MetaFactory 实体 DSL，模式同 NotificationUserService）：只更新绑定相关列，
        // 不走实体全字段保存链（其审计字段会话级覆盖在匿名请求下会把 updater 置 null 触发 1048）
        updateBindingInDb(unionId, target.getId());
        target.setWeixinUnionId(unionId);
        refreshUserSnapshot();
        publishSafely(new MiniAppBoundEvent(target.getId(), target.getLoginName(),
                tail(unionId), maskMobile(target.getMobilePhone())));
        return buildLoginResult(target);
    }

    /** 解绑：unionid 定位绑定账号并清空 weixin_unionId（换绑前提）。 */
    public void unbind(String code) {
        // ===== [DEBUG-REMOVE-BEGIN] 联调调试通道：test_{账号}_bind 按账号定位清绑（模拟已绑定解绑）；test_{账号}_unbind 模拟未绑定 401。未来整体删除本段 =====
        if (properties.isDebugEnabled()) {
            Optional<DebugLoginCode.DebugCode> debug = DebugLoginCode.parse(code);
            if (debug.isPresent()) {
                DebugLoginCode.DebugCode dc = debug.get();
                if (dc.mode() == DebugLoginCode.Mode.UNBIND) {
                    log.info("[调试] 模拟未绑定场景, account={}", dc.account());
                    throw new MiniAppUnauthorizedException(MiniAppErrorCodes.NOT_BOUND, "当前微信未绑定账号");
                }
                log.warn("[调试] 跳过微信认证, 按账号 {} 清除绑定（debug-enabled=true，生产严禁开启）", dc.account());
                User user = findDebugUserByLoginName(dc.account());
                clearBindingInDb(user.getId());
                refreshUserSnapshot();
                publishSafely(new MiniAppUnboundEvent(user.getId(), user.getLoginName(), tail(DebugLoginCode.debugUnionId(dc.account()))));
                return;
            }
        }
        // ===== [DEBUG-REMOVE-END] =====
        String unionId = resolveUnionId(code);
        User user = findBoundUser(unionId);
        clearBindingInDb(user.getId());
        refreshUserSnapshot();
        publishSafely(new MiniAppUnboundEvent(user.getId(), user.getLoginName(), tail(unionId)));
    }

    /**
     * 绑定写入：MetaFactory 实体 DSL 精确列更新（模式同 NotificationUserService 软删除），
     * 只更新绑定相关列，不回写实体其余字段（规避 Dao 保存链全字段回写）。
     * 审计字段（updateAt/updater/updaterName）由匿名上下文拦截器设定的虚拟身份（miniapp）
     * 经 PlatformFieldValueFillSupport 填充，无需在此显式指定。protected 便于单测桩替。
     */
    protected String updateBindingInDb(String unionId, String userId) {
        return MetaFactory.update(User.class)
                .where(Filter.eq("id", userId), Filter.eq("delStatus", 0), Filter.eq("enableStatus", 1))
                .value("weixinUnionId", unionId)
                .save();
    }

    /**
     * 解绑写入：weixin_unionId 置空串（约定 NULL/空串均为未绑定，与绑定查询语义一致）。
     */
    protected String clearBindingInDb(String userId) {
        return MetaFactory.update(User.class)
                .where(Filter.eq("id", userId), Filter.eq("delStatus", 0))
                .value("weixinUnionId", "")
                .save();
    }

    /**
     * 绑定/解绑写库后刷新用户安全快照：platform_user.weixin_unionId 的 extend 索引由
     * UserProvider 内存快照承载（启动/refresh 时加载），不刷新的话 WeixinCode/WeixinUnionId
     * 等认证器经 getUserByExtendKey 反查会一直查到旧快照——刚绑定的 unionid 反查不到（401），
     * 刚解绑的 unionid 还能查到（吊销延迟）。失败仅告警不影响主流程（快照会随下次刷新收敛）。
     */
    private void refreshUserSnapshot() {
        if (refreshCoordinator == null) {
            return;
        }
        try {
            refreshCoordinator.refreshUser();
        } catch (Exception e) {
            log.warn("绑定/解绑后刷新用户安全快照失败（认证器反查或存在短暂延迟，随下次刷新收敛）", e);
        }
    }

    private String resolveUnionId(String code) {
        String unionId = weixinCodeResolver.resolveUnionId(code);
        if (unionId == null || unionId.isEmpty()) {
            throw new MiniAppUnauthorizedException(MiniAppErrorCodes.IDENTITY_UNAVAILABLE, "无法获取微信身份标识，请联系管理员");
        }
        return unionId;
    }

    // ===== [DEBUG-REMOVE-BEGIN] 以下两个私有方法仅供联调调试通道使用，未来整体删除 =====
    /** 联调调试：debug code 构造确定性假 unionid；debug 关闭或非 test_ 格式走正常解析。 */
    private String debugUnionIdOrResolve(String code) {
        if (properties.isDebugEnabled()) {
            Optional<DebugLoginCode.DebugCode> debug = DebugLoginCode.parse(code);
            if (debug.isPresent()) {
                String unionId = DebugLoginCode.debugUnionId(debug.get().account());
                log.warn("[调试] bind 使用假 unionid {}（debug-enabled=true，生产严禁开启）", unionId);
                return unionId;
            }
        }
        return resolveUnionId(code);
    }

    /** 联调调试：按 loginName 定位调试账号；不存在按"未绑定"处理并提示（debug 专用路径）。 */
    private User findDebugUserByLoginName(String account) {
        List<User> users = userService.queryModel(User.class, Map.of("loginName", account));
        if (users == null || users.isEmpty()) {
            log.warn("[调试] 账号 {} 不存在（请确认 login_name）", account);
            throw new MiniAppUnauthorizedException(MiniAppErrorCodes.NOT_BOUND, "请先绑定账号");
        }
        User user = users.get(0);
        if (user.getEnableStatus() != ENABLE_STATUS_ENABLED) {
            throw new MiniAppAccountDisabledException();
        }
        return user;
    }

    private User findBoundUser(String unionId) {
        List<User> users = userService.queryModel(User.class, Map.of("weixinUnionId", unionId));
        if (users == null || users.isEmpty()) {
            throw new MiniAppUnauthorizedException(MiniAppErrorCodes.NOT_BOUND, "请先绑定账号");
        }
        if (users.size() > 1) {
            // 严格一对一由本服务维护，出现多条属历史数据异常：取第一条并告警
            log.warn("weixin_unionId={} 命中 {} 条绑定（历史数据异常），取第一条", tail(unionId), users.size());
        }
        User user = users.get(0);
        if (user.getEnableStatus() != ENABLE_STATUS_ENABLED) {
            throw new MiniAppAccountDisabledException();
        }
        return user;
    }
    // ===== [DEBUG-REMOVE-END] =====

    private MiniAppLoginResult buildLoginResult(User user) {
        // 与 /api/user/info 同构的完整身份（roles/orgs/tenants/companyName 等）
        LoginResult full = currentUserProfileService.buildProfile(
                user, user.getOrgId(), user.getTenantCode(), null, null, null);
        MiniAppLoginResult result = MiniAppLoginResult.of(full);

        int ttlSeconds = properties.getTokenTtlSeconds();
        try {
            Map<String, String> payload = new HashMap<>(5);
            payload.put("id", user.getId());
            payload.put("loginName", user.getLoginName());
            payload.put("passWord", GlobalContext.getAnonymousPwd());
            payload.put("orgId", user.getOrgId());
            payload.put("tenantCode", user.getTenantCode());
            result.setToken(JWTUtil.getToken(payload, ttlSeconds));
        } catch (Exception e) {
            log.error("签发小程序会话 token 失败, userId={}", user.getId(), e);
            throw new IllegalStateException("签发会话令牌失败", e);
        }
        result.setExpiresIn(ttlSeconds);

        // 业务字段增强（角色/客户名等）：失败不阻断登录，由实现方自行容错
        loginResultEnhancer.enhance(result, user);
        return result;
    }

    private void publishSafely(Object event) {
        try {
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.warn("小程序绑定/解绑事件发布失败（不影响主流程）: {}", event.getClass().getSimpleName(), e);
        }
    }

    /** 姓名脱敏：保留首字，其余 *（两字→张*，三字及以上→张**）。 */
    static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.length() == 1) {
            return name;
        }
        return name.charAt(0) + "*".repeat(name.length() - 1);
    }

    /** 手机号脱敏：138****1234。 */
    static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    static String tail(String value) {
        if (value == null || value.length() <= 6) {
            return value;
        }
        return value.substring(value.length() - 6);
    }
}
