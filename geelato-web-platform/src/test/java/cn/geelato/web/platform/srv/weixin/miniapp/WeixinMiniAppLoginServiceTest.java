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
    private User updatedUser;

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

            @Override
            public <T extends cn.geelato.core.meta.model.entity.BaseEntity> T updateModel(T model) {
                if (model instanceof User u) {
                    updatedUser = u;
                }
                return model;
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
        return new WeixinMiniAppLoginService(
                userService,
                jsCode -> UNION_ID,
                accountResolver,
                (result, user) -> { /* noop：增强器由应用实现，平台单测不覆盖 */ },
                ticketService,
                apiClient,
                properties,
                publishedEvents::add);
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
        MiniAppNotBoundException e = assertThrows(MiniAppNotBoundException.class, () -> service.login("any-code"));
        assertEquals(20011, e.getErrorCode());
    }

    @Test
    void login_disabledAccount_throws20012() {
        boundUser.setEnableStatus(0);
        boundUsers.add(boundUser);
        WeixinMiniAppLoginService service = newService(stubUserService(), p -> Optional.empty());
        MiniAppAccountDisabledException e = assertThrows(MiniAppAccountDisabledException.class, () -> service.login("any-code"));
        assertEquals(20012, e.getErrorCode());
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
        MiniAppUnionIdOccupiedException e = assertThrows(MiniAppUnionIdOccupiedException.class,
                () -> service.bind("any-code", ticket));
        assertEquals(20013, e.getErrorCode());
    }

    @Test
    void bind_targetAlreadyBoundToOtherWeChat_throws20014() {
        boundUser.setWeixinUnionId("union-other");
        WeixinMiniAppLoginService service = newService(stubUserService(), p -> Optional.empty());
        String ticket = ticketOf("user-001");
        MiniAppAccountBoundException e = assertThrows(MiniAppAccountBoundException.class,
                () -> service.bind("any-code", ticket));
        assertEquals(20014, e.getErrorCode());
    }

    @Test
    void bind_success_writesUnionIdAndPublishesEvent() {
        WeixinMiniAppLoginService service = newService(stubUserService(), p -> Optional.empty());
        String ticket = ticketOf("user-001");
        MiniAppLoginResult result = service.bind("any-code", ticket);
        assertEquals("user-001", result.getId());
        assertEquals(UNION_ID, updatedUser.getWeixinUnionId());
        assertEquals(1, publishedEvents.size());
        assertTrue(publishedEvents.get(0) instanceof MiniAppBoundEvent);
        assertEquals("user-001", ((MiniAppBoundEvent) publishedEvents.get(0)).getUserId());
    }

    // ---- unbind ----

    @Test
    void unbind_notBound_throws20011() {
        WeixinMiniAppLoginService service = newService(stubUserService(), p -> Optional.empty());
        MiniAppNotBoundException e = assertThrows(MiniAppNotBoundException.class, () -> service.unbind("any-code"));
        assertEquals(20011, e.getErrorCode());
    }

    @Test
    void unbind_bound_clearsUnionIdAndPublishesEvent() {
        boundUser.setWeixinUnionId(UNION_ID);
        boundUsers.add(boundUser);
        WeixinMiniAppLoginService service = newService(stubUserService(), p -> Optional.empty());
        service.unbind("any-code");
        assertNull(updatedUser.getWeixinUnionId());
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

    /** 独立签发一张指向 user-001 的有效票据（走真实 TicketService，手机号与桩一致）。 */
    private String ticketOf(String userId) {
        MiniAppTicketService ticketService = new MiniAppTicketService(properties);
        return ticketService.issue(userId, "13812341234");
    }
}
