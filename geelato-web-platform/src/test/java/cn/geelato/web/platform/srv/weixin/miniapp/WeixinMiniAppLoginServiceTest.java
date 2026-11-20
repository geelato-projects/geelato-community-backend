package cn.geelato.web.platform.srv.weixin.miniapp;

import cn.geelato.meta.User;
import cn.geelato.web.platform.boot.properties.WeixinMiniAppConfigurationProperties;
import cn.geelato.web.platform.srv.security.service.UserService;
import cn.geelato.web.platform.srv.weixin.miniapp.entity.MiniAppAccount;
import cn.geelato.web.platform.srv.weixin.miniapp.entity.MiniAppBoundEvent;
import cn.geelato.web.platform.srv.weixin.miniapp.entity.MiniAppLoginResult;
import cn.geelato.web.platform.srv.weixin.miniapp.entity.MiniAppUnboundEvent;
import cn.geelato.web.platform.srv.weixin.miniapp.service.MiniAppTicketService;
import cn.geelato.web.platform.srv.weixin.miniapp.service.WeixinMiniAppApiClient;
import cn.geelato.web.platform.srv.weixin.miniapp.service.WeixinMiniAppLoginService;
import cn.geelato.web.platform.srv.weixin.miniapp.spi.MiniAppAccountResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 登录/绑定编排单元测试：未绑定 20011、停用 20012、双向冲突 20013/20014、
 * 成功路径（token 签发 / 绑定写入 + 事件 / 解绑清空 / pre-bind 脱敏与票据）。
 * UserService 以匿名子类覆写查询/更新方法做桩（不触底层 dao）；事件经 lambda publisher 捕获；
 * ApiClient 覆写 getPhoneNumber 隔离微信外呼。
 */
class WeixinMiniAppLoginServiceTest {

    private static final String UNION_ID = "union-abc-123456";

    private WeixinMiniAppConfigurationProperties properties;
    private List<Object> publishedEvents;
    private User boundUser;
    private List<User> boundUsers;
    private String capturedBindingUnionId;
    private String capturedUpdaterId;

    @BeforeEach
    void setUp() {
        properties = new WeixinMiniAppConfigurationProperties();
        properties.setTicketKey("unit-test-ticket-key");
        properties.setTicketTtlSeconds(300);
        properties.setTokenTtlSeconds(604800);
        publishedEvents = new ArrayList<>();
        boundUser = user("user-001", "cs_zhangsan", "张三", 1);
        boundUsers = new ArrayList<>();
    }

    // ---- 桩 ----

    /** 覆写 queryModel/getModel/updateModel（保持父类泛型签名），不触底层 dao。 */
    private UserService stubUserService() {
        return new UserService() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> List<T> queryModel(Class<T> entity, Map<String, Object> params) {
                List<User> matched = matchUsers(params);
                return (List<T>) matched;
            }

            @Override
            public <T> T getModel(Class<T> entity, String id) {
                if (entity == User.class && "user-001".equals(id)) {
                    return entity.cast(boundUser);
                }
                return null;
            }

        };
    }

    private List<User> matchUsers(Map<String, Object> params) {
        Object unionId = params.get("weixinUnionId");
        if (unionId != null) {
            return boundUsers;
        }
        Object mobile = params.get("mobilePhone");
        if (mobile != null && mobile.equals(boundUser.getMobilePhone())) {
            return new ArrayList<>(List.of(boundUser));
        }
        return new ArrayList<>();
    }

    private WeixinMiniAppLoginService newService(UserService userService,
                                                 MiniAppAccountResolver accountResolver) {
        MiniAppTicketService ticketService = new MiniAppTicketService(properties);
        WeixinMiniAppApiClient apiClient = new WeixinMiniAppApiClient(properties) {
            @Override
            public String getPhoneNumber(String phoneCode) {
                return "13812341234";
            }
        };
        // 匿名子类桩替 protected 写库方法（隔离 MetaFactory/真实数据源），捕获写库参数供断言
        return new WeixinMiniAppLoginService(
                userService,
                jsCode -> UNION_ID,
                accountResolver,
                (result, user) -> { /* noop：增强器由应用实现，平台单测不覆盖 */ },
                ticketService,
                apiClient,
                properties,
                publishedEvents::add,
                null /* 快照刷新协调器：单测环境无安全快照，跳过刷新 */) {
            @Override
            protected String updateBindingInDb(String unionId, String userId) {
                capturedBindingUnionId = unionId;
                capturedUpdaterId = userId;
                return userId;
            }

            @Override
            protected String clearBindingInDb(String userId) {
                capturedBindingUnionId = null;
                capturedUpdaterId = userId;
                return userId;
            }
        };
    }

    private static User user(String id, String loginName, String name, int enableStatus) {
        User user = new User();
        user.setId(id);
        user.setLoginName(loginName);
        user.setName(name);
        user.setEnableStatus(enableStatus);
        user.setMobilePhone("13812341234");
        user.setOrgId("org-1");
        user.setTenantCode("geelato");
        user.setCreateAt(new Date());
        return user;
    }

    // ---- login ----

    @Test
    void login_notBound_throws20011() {
        WeixinMiniAppLoginService service = newService(stubUserService(), p -> Optional.empty());
        MiniAppUnauthorizedException e = assertThrows(MiniAppUnauthorizedException.class, () -> service.login("any-code"));
        assertEquals(MiniAppErrorCodes.NOT_BOUND, e.getErrorCode());
    }

    @Test
    void login_disabledAccount_throws20012() {
        boundUser.setEnableStatus(0);
        boundUsers.add(boundUser);
        WeixinMiniAppLoginService service = newService(stubUserService(), p -> Optional.empty());
        MiniAppAccountDisabledException e = assertThrows(MiniAppAccountDisabledException.class, () -> service.login("any-code"));
        assertEquals(MiniAppErrorCodes.ACCOUNT_DISABLED, e.getErrorCode());
    }

    @Test
    void login_bound_succeedsWithTokenAndIdentity() {
        boundUser.setWeixinUnionId(UNION_ID);
        boundUsers.add(boundUser);
        WeixinMiniAppLoginService service = newService(stubUserService(), p -> Optional.empty());
        MiniAppLoginResult result = service.login("any-code");
        assertEquals("user-001", result.getId());
        assertNotNull(result.getToken());
        assertEquals(Integer.valueOf(604800), result.getExpiresIn());
        assertEquals(UNION_ID, result.getWeixinUnionId());
    }

    // ---- bind ----

    @Test
    void bind_unionIdOccupiedByOther_throws20013() {
        boundUsers.add(user("user-other", "cs_other", "其他", 1));
        WeixinMiniAppLoginService service = newService(stubUserService(), p -> Optional.empty());
        String ticket = ticketOf("user-001");
        MiniAppBindConflictException e = assertThrows(MiniAppBindConflictException.class,
                () -> service.bind("any-code", ticket));
        assertEquals(MiniAppErrorCodes.UNION_ID_OCCUPIED, e.getErrorCode());
    }

    @Test
    void bind_targetAlreadyBoundToOtherWeChat_throws20014() {
        boundUser.setWeixinUnionId("union-other");
        WeixinMiniAppLoginService service = newService(stubUserService(), p -> Optional.empty());
        String ticket = ticketOf("user-001");
        MiniAppBindConflictException e = assertThrows(MiniAppBindConflictException.class,
                () -> service.bind("any-code", ticket));
        assertEquals(MiniAppErrorCodes.ACCOUNT_ALREADY_BOUND, e.getErrorCode());
    }

    @Test
    void bind_success_writesUnionIdAndPublishesEvent() {
        WeixinMiniAppLoginService service = newService(stubUserService(), p -> Optional.empty());
        String ticket = ticketOf("user-001");
        MiniAppLoginResult result = service.bind("any-code", ticket);
        assertEquals("user-001", result.getId());
        assertEquals(UNION_ID, capturedBindingUnionId);
        // 精确列更新自带审计字段（updater=账号自身，platform_user.updater NOT NULL）
        assertEquals("user-001", capturedUpdaterId);
        assertEquals(1, publishedEvents.size());
        assertTrue(publishedEvents.get(0) instanceof MiniAppBoundEvent);
        assertEquals("user-001", ((MiniAppBoundEvent) publishedEvents.get(0)).getUserId());
    }

    // ---- unbind ----

    @Test
    void unbind_notBound_throws20011() {
        WeixinMiniAppLoginService service = newService(stubUserService(), p -> Optional.empty());
        MiniAppUnauthorizedException e = assertThrows(MiniAppUnauthorizedException.class, () -> service.unbind("any-code"));
        assertEquals(MiniAppErrorCodes.NOT_BOUND, e.getErrorCode());
    }

    @Test
    void unbind_bound_clearsUnionIdAndPublishesEvent() {
        boundUser.setWeixinUnionId(UNION_ID);
        boundUsers.add(boundUser);
        WeixinMiniAppLoginService service = newService(stubUserService(), p -> Optional.empty());
        service.unbind("any-code");
        assertNull(capturedBindingUnionId);
        assertEquals("user-001", capturedUpdaterId);
        assertEquals(1, publishedEvents.size());
        assertTrue(publishedEvents.get(0) instanceof MiniAppUnboundEvent);
    }

    // ---- pre-bind ----

    @Test
    void preBind_noAccountMatched_returnsMatchedFalse() {
        MiniAppAccountResolver emptyResolver = p -> Optional.empty();
        WeixinMiniAppLoginService service = newService(stubUserService(), emptyResolver);
        var result = service.preBind("phone-code");
        assertEquals(false, result.isMatched());
    }

    @Test
    void preBind_matched_returnsMaskedAccountAndTicket() {
        MiniAppAccountResolver resolver = p -> {
            MiniAppAccount account = new MiniAppAccount();
            account.setUserId("user-001");
            account.setLoginName("cs_zhangsan");
            account.setUserName("张三");
            account.setMobile("13812341234");
            account.setOrgName("某某国际贸易有限公司");
            return Optional.of(account);
        };
        WeixinMiniAppLoginService service = newService(stubUserService(), resolver);
        var result = service.preBind("phone-code");
        assertTrue(result.isMatched());
        assertEquals("张*", result.getAccount().getUserName());
        assertEquals("138****1234", result.getAccount().getMobile());
        assertEquals("某某国际贸易有限公司", result.getAccount().getOrgName());
        assertNotNull(result.getPreBindTicket());
    }

    // ===== [DEBUG-REMOVE-BEGIN] phoneCode 调试与票据摘要一致性校验用例 =====

    @Test
    void debugPhoneCode_preBind_matchedDirectlyWithoutWeChatCall() {
        properties.setDebugEnabled(true);
        MiniAppAccountResolver resolver = p -> {
            MiniAppAccount account = new MiniAppAccount();
            account.setUserId("user-001");
            account.setLoginName("cs_zhangsan");
            account.setUserName("张三");
            account.setMobile(p);
            account.setOrgName("某某国际贸易有限公司");
            return Optional.of(account);
        };
        WeixinMiniAppLoginService service = newService(stubUserService(), resolver);
        // phoneCode 用调试格式（真实 ApiClient 的 debug 分支行为在 ApiClient 层，这里 resolver 命中同手机号）
        var result = service.preBind("test_13812341234");
        assertTrue(result.isMatched());
        assertEquals("138****1234", result.getAccount().getMobile());
    }

    @Test
    void bind_phoneDigestMismatch_throws20009() {
        // 票据签发后账号手机号已变更：摘要不一致 → 20009
        WeixinMiniAppLoginService service = newService(stubUserService(), p -> Optional.empty());
        MiniAppTicketService ticketService = new MiniAppTicketService(properties);
        String staleTicket = ticketService.issue("user-001", "13900000000"); // 与账号当前手机号不一致
        MiniAppUnauthorizedException e = assertThrows(MiniAppUnauthorizedException.class,
                () -> service.bind("any-code", staleTicket));
        assertEquals(MiniAppErrorCodes.TICKET_INVALID, e.getErrorCode());
    }
    // ===== [DEBUG-REMOVE-END] =====

    /** 独立签发一张指向 user-001 的有效票据（走真实 TicketService，手机号与桩一致）。 */
    private String ticketOf(String userId) {
        MiniAppTicketService ticketService = new MiniAppTicketService(properties);
        return ticketService.issue(userId, "13812341234");
    }

    // ===== [DEBUG-REMOVE-BEGIN] 联调调试通道用例：随调试功能整体删除（含 debugLogin/debugBind/debugUnbind/debugCode 开头的用例）=====

    @Test
    void debugLogin_bindMode_logsInDirectlyByLoginName() {
        properties.setDebugEnabled(true);
        // 桩：按 loginName 查询返回 user-001
        UserService userService = new UserService() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> List<T> queryModel(Class<T> entity, Map<String, Object> params) {
                if ("cs_zhangsan".equals(params.get("loginName"))) {
                    return (List<T>) new ArrayList<>(List.of(boundUser));
                }
                return java.util.Collections.emptyList();
            }

            @Override
            public <T> T getModel(Class<T> entity, String id) {
                return null;
            }
        };
        WeixinMiniAppLoginService service = newService(userService, p -> Optional.empty());
        MiniAppLoginResult result = service.login("test_cs_zhangsan_bind");
        assertEquals("user-001", result.getId());
        assertNotNull(result.getToken());
    }

    @Test
    void debugLogin_unbindMode_simulatesNotBound() {
        properties.setDebugEnabled(true);
        WeixinMiniAppLoginService service = newService(stubUserService(), p -> Optional.empty());
        MiniAppUnauthorizedException e = assertThrows(MiniAppUnauthorizedException.class,
                () -> service.login("test_user001_unbind"));
        assertEquals(MiniAppErrorCodes.NOT_BOUND, e.getErrorCode());
    }

    @Test
    void debugBind_usesDeterministicFakeUnionId() {
        properties.setDebugEnabled(true);
        WeixinMiniAppLoginService service = newService(stubUserService(), p -> Optional.empty());
        String ticket = ticketOf("user-001");
        service.bind("test_user001_bind", ticket);
        assertEquals("debug-union-user001", capturedBindingUnionId);
    }

    @Test
    void debugUnbind_bindMode_clearsBindingByLoginName() {
        properties.setDebugEnabled(true);
        boundUser.setWeixinUnionId("debug-union-user001");
        UserService userService = new UserService() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> List<T> queryModel(Class<T> entity, Map<String, Object> params) {
                if ("cs_zhangsan".equals(params.get("loginName"))) {
                    return (List<T>) new ArrayList<>(List.of(boundUser));
                }
                return java.util.Collections.emptyList();
            }

            @Override
            public <T> T getModel(Class<T> entity, String id) {
                return null;
            }

        };
        WeixinMiniAppLoginService service = newService(userService, p -> Optional.empty());
        service.unbind("test_cs_zhangsan_bind");
        assertNull(capturedBindingUnionId);
        assertTrue(publishedEvents.get(0) instanceof MiniAppUnboundEvent);
    }

    @Test
    void debugCode_ignoredWhenSwitchOff() {
        // 开关关闭（默认）：test_ 格式走正常解析 → resolver 返回 unionid → 按绑定查询
        WeixinMiniAppLoginService service = newService(stubUserService(), p -> Optional.empty());
        MiniAppUnauthorizedException e = assertThrows(MiniAppUnauthorizedException.class,
                () -> service.login("test_user001_bind"));
        assertEquals(MiniAppErrorCodes.NOT_BOUND, e.getErrorCode());
    }

    @Test
    void debugLogin_accountNotFound_throws20011() {
        properties.setDebugEnabled(true);
        UserService emptyByName = new UserService() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> List<T> queryModel(Class<T> entity, Map<String, Object> params) {
                return java.util.Collections.emptyList();
            }

            @Override
            public <T> T getModel(Class<T> entity, String id) {
                return null;
            }
        };
        WeixinMiniAppLoginService service = newService(emptyByName, p -> Optional.empty());
        MiniAppUnauthorizedException e = assertThrows(MiniAppUnauthorizedException.class,
                () -> service.login("test_nobody_bind"));
        assertEquals(MiniAppErrorCodes.NOT_BOUND, e.getErrorCode());
    }
    // ===== [DEBUG-REMOVE-END] =====
}
