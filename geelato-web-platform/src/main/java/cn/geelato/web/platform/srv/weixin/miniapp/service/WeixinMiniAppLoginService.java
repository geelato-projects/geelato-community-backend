package cn.geelato.web.platform.srv.weixin.miniapp.service;

import cn.geelato.core.GlobalContext;
import cn.geelato.meta.User;
import cn.geelato.web.common.security.JWTUtil;
import cn.geelato.web.common.security.spi.WeixinCodeResolver;
import cn.geelato.web.platform.boot.properties.WeixinMiniAppConfigurationProperties;
import cn.geelato.web.platform.srv.security.entity.LoginResult;
import cn.geelato.web.platform.srv.security.service.UserService;
import cn.geelato.web.platform.srv.weixin.miniapp.MiniAppAccountDisabledException;
import cn.geelato.web.platform.srv.weixin.miniapp.MiniAppIdentityUnavailableException;
import cn.geelato.web.platform.srv.weixin.miniapp.MiniAppNotBoundException;
import cn.geelato.web.platform.srv.weixin.miniapp.MiniAppTicketInvalidException;
import cn.geelato.web.platform.srv.weixin.miniapp.MiniAppUnionIdOccupiedException;
import cn.geelato.web.platform.srv.weixin.miniapp.MiniAppAccountBoundException;
import cn.geelato.web.platform.srv.weixin.miniapp.entity.MiniAppAccount;
import cn.geelato.web.platform.srv.weixin.miniapp.entity.MiniAppBoundEvent;
import cn.geelato.web.platform.srv.weixin.miniapp.entity.MiniAppLoginResult;
import cn.geelato.web.platform.srv.weixin.miniapp.entity.MiniAppPreBindAccount;
import cn.geelato.web.platform.srv.weixin.miniapp.entity.MiniAppPreBindResult;
import cn.geelato.web.platform.srv.weixin.miniapp.entity.MiniAppUnboundEvent;
import cn.geelato.web.platform.srv.weixin.miniapp.spi.MiniAppAccountResolver;
import cn.geelato.web.platform.srv.weixin.miniapp.spi.MiniAppLoginResultEnhancer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public WeixinMiniAppLoginService(UserService userService,
                                     WeixinCodeResolver weixinCodeResolver,
                                     MiniAppAccountResolver accountResolver,
                                     MiniAppLoginResultEnhancer loginResultEnhancer,
                                     MiniAppTicketService ticketService,
                                     WeixinMiniAppApiClient apiClient,
                                     WeixinMiniAppConfigurationProperties properties,
                                     ApplicationEventPublisher eventPublisher) {
        this.userService = userService;
        this.weixinCodeResolver = weixinCodeResolver;
        this.accountResolver = accountResolver;
        this.loginResultEnhancer = loginResultEnhancer;
        this.ticketService = ticketService;
        this.apiClient = apiClient;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    /** 静默登录：unionid 反查绑定账号，命中即返回身份（内含 token）；未绑定抛 20011。 */
    public MiniAppLoginResult login(String code) {
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
        String targetUserId = ticketService.verifyAndConsume(preBindTicket);
        String unionId = resolveUnionId(code);

        User target = userService.getModel(User.class, targetUserId);
        if (target == null) {
            // 票据签发后账号被删：等同票据失效
            throw new MiniAppTicketInvalidException();
        }
        if (target.getEnableStatus() != ENABLE_STATUS_ENABLED) {
            throw new MiniAppAccountDisabledException();
        }
        // 双向严格一对一查重
        List<User> occupied = userService.queryModel(User.class, Map.of("weixinUnionId", unionId));
        if (occupied != null && occupied.stream().anyMatch(u -> !targetUserId.equals(u.getId()))) {
            throw new MiniAppUnionIdOccupiedException();
        }
        if (target.getWeixinUnionId() != null && !target.getWeixinUnionId().isEmpty()
                && !target.getWeixinUnionId().equals(unionId)) {
            throw new MiniAppAccountBoundException();
        }

        target.setWeixinUnionId(unionId);
        userService.updateModel(target);
        publishSafely(new MiniAppBoundEvent(target.getId(), target.getLoginName(),
                tail(unionId), maskMobile(target.getMobilePhone())));
        return buildLoginResult(target);
    }

    /** 解绑：unionid 定位绑定账号并清空 weixin_unionId（换绑前提）。 */
    public void unbind(String code) {
        String unionId = resolveUnionId(code);
        User user = findBoundUser(unionId);
        user.setWeixinUnionId(null);
        userService.updateModel(user);
        publishSafely(new MiniAppUnboundEvent(user.getId(), user.getLoginName(), tail(unionId)));
    }

    private String resolveUnionId(String code) {
        String unionId = weixinCodeResolver.resolveUnionId(code);
        if (unionId == null || unionId.isEmpty()) {
            throw new MiniAppIdentityUnavailableException();
        }
        return unionId;
    }

    private User findBoundUser(String unionId) {
        List<User> users = userService.queryModel(User.class, Map.of("weixinUnionId", unionId));
        if (users == null || users.isEmpty()) {
            throw new MiniAppNotBoundException();
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

    private MiniAppLoginResult buildLoginResult(User user) {
        LoginResult formatted = LoginResult.formatLoginResult(user);
        MiniAppLoginResult result = new MiniAppLoginResult();
        // 平台标准字段拷贝（formatLoginResult 返回 LoginResult，字段逐项搬入子类）
        result.setId(formatted.getId());
        result.setJobNumber(formatted.getJobNumber());
        result.setName(formatted.getName());
        result.setLoginName(formatted.getLoginName());
        result.setAvatar(formatted.getAvatar());
        result.setMobilePrefix(formatted.getMobilePrefix());
        result.setMobilePhone(formatted.getMobilePhone());
        result.setEmail(formatted.getEmail());
        result.setRegistrationDate(formatted.getRegistrationDate());
        result.setOrgId(formatted.getOrgId());
        result.setOrgName(formatted.getOrgName());
        result.setNationCode(formatted.getNationCode());
        result.setProvinceCode(formatted.getProvinceCode());
        result.setCityCode(formatted.getCityCode());
        result.setAddress(formatted.getAddress());
        result.setDescription(formatted.getDescription());
        result.setTenantCode(formatted.getTenantCode());
        result.setCooperatingOrgId(formatted.getCooperatingOrgId());
        result.setEnableStatus(formatted.getEnableStatus());
        result.setCompanyId(formatted.getCompanyId());
        result.setWeixinUnionId(formatted.getWeixinUnionId());

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
