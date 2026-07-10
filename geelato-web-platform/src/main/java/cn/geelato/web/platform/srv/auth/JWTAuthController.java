package cn.geelato.web.platform.srv.auth;

import cn.geelato.core.GlobalContext;
import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.UserOrg;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.common.constants.MediaTypes;
import cn.geelato.web.common.interceptor.annotation.IgnoreVerify;
import cn.geelato.meta.User;
import cn.geelato.web.common.shiro.ShiroUser;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.security.entity.*;
import cn.geelato.web.platform.srv.auth.service.AccountRecoveryService;
import cn.geelato.web.platform.srv.auth.service.CurrentUserProfileService;
import cn.geelato.web.platform.srv.auth.service.UserAccountCommandService;
import cn.geelato.web.platform.srv.auth.service.UserAuthorizationQueryService;
import cn.geelato.web.platform.srv.auth.service.UserIdentityQueryService;
import cn.geelato.web.platform.utils.JWTUtil;
import cn.geelato.web.platform.utils.EncryptUtil;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@ApiRestController("/user")
@Slf4j
@Validated
public class JWTAuthController extends BaseController {
    private final UserIdentityQueryService userIdentityQueryService;
    private final CurrentUserProfileService currentUserProfileService;
    private final UserAuthorizationQueryService userAuthorizationQueryService;
    private final UserAccountCommandService userAccountCommandService;
    private final AccountRecoveryService accountRecoveryService;
    private static final String anonymousFixedPassword = GlobalContext.getAnonymousPwd();

    public JWTAuthController(UserIdentityQueryService userIdentityQueryService,
                             CurrentUserProfileService currentUserProfileService,
                             UserAuthorizationQueryService userAuthorizationQueryService,
                             UserAccountCommandService userAccountCommandService,
                             AccountRecoveryService accountRecoveryService) {
        this.userIdentityQueryService = userIdentityQueryService;
        this.currentUserProfileService = currentUserProfileService;
        this.userAuthorizationQueryService = userAuthorizationQueryService;
        this.userAccountCommandService = userAccountCommandService;
        this.accountRecoveryService = accountRecoveryService;
    }

    @IgnoreVerify
    @RequestMapping(value = "/login", method = RequestMethod.POST, produces = {MediaTypes.APPLICATION_JSON_UTF_8})
    public ApiResult<LoginResult> login(@Valid @RequestBody LoginParams loginParams) {
        return doLogin(loginParams, false);
    }

    @RequestMapping(value = "/login/anonymous", method = RequestMethod.POST, produces = {MediaTypes.APPLICATION_JSON_UTF_8})
    public ApiResult<LoginResult> loginAnonymous(@Valid @RequestBody LoginParams loginParams) {
        return doLogin(loginParams, true);
    }

    private ApiResult<LoginResult> doLogin(LoginParams loginParams, boolean anonymousMode) {

        if(!GlobalContext.getAnonymousOption()){
            return ApiResult.fail("不允许匿名登录");
        }
        try {
            if (anonymousMode) {
                if (!anonymousFixedPassword.equals(loginParams.getPassword())) {
                    return ApiResult.fail("固定密码不正确");
                }
            }

            String tenantCodeParam = StringUtils.isNotEmpty(loginParams.getSuffix()) ? loginParams.getSuffix().replace("@", "") : loginParams.getTenant();
            FilterGroup filterGroup = new FilterGroup();
            filterGroup.addFilter("loginName", loginParams.getUsername());
            filterGroup.addFilter("enableStatus", "1");
            filterGroup.addFilter("delStatus", "0");
            if (StringUtils.isNotBlank(tenantCodeParam)) {
                filterGroup.addFilter("tenantCode", tenantCodeParam);
            }
            List<User> loginUsers = dao.queryList(User.class, filterGroup, "");
            if (loginUsers == null || loginUsers.isEmpty()) {
                return ApiResult.fail(anonymousMode ? "账号不存在或不可用" : "账号或密码不正确");
            }
            if (loginUsers.size() > 1) {
                List<Tenant> tenantList = userIdentityQueryService.queryTenantListByLoginName(loginParams.getUsername());
                if (tenantList.size() > 1) {
                    LoginResult loginResult = new LoginResult();
                    loginResult.setTenants(tenantList);
                    return ApiResult.fail(loginResult, LoginMultiTenantException.DEFAULT_CODE, "请选择租户");
                }
                return ApiResult.fail("账号信息不唯一，请联系管理员");
            }

            User loginUser = loginUsers.get(0);
            if (!anonymousMode) {
                Boolean checkPsdRst = checkPsd(loginUser, loginParams);
                if (!checkPsdRst) {
                    return ApiResult.fail("账号或密码不正确");
                }
            }

            String userId = loginUser.getId();
            Map<String, String> payload = new HashMap<>(5);
            payload.put("id", userId);
            payload.put("loginName", loginUser.getLoginName());
            payload.put("passWord", loginParams.getPassword());
            payload.put("orgId", loginUser.getOrgId());
            payload.put("tenantCode", loginUser.getTenantCode());
            if(anonymousMode){
                payload.put("anonymous", anonymousFixedPassword);
            }
            String token = JWTUtil.getToken(payload);

            LoginResult loginResult = LoginResult.formatLoginResult(loginUser);
            loginResult.setToken(token);
            return ApiResult.success(loginResult, anonymousMode ? "匿名认证成功!" : "认证成功!");
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
            return ApiResult.fail("服务暂不可用，请稍后重试!");
        }
    }

    @RequestMapping(value = "/switchIdentity", method = RequestMethod.GET, produces = {MediaTypes.APPLICATION_JSON_UTF_8})
    public ApiResult<LoginResult> switchIdentity(String org, String tenant) throws Exception {
        String userId = SecurityContext.getCurrentUser().getUserId();
        User loginUser = dao.queryForObject(User.class, "id", userId);
        List<Tenant> tenantList = userIdentityQueryService.queryTenantListByUserId(userId);
        List<UserOrg> userOrgList = userIdentityQueryService.queryOrgListByUserId(userId);

        String orgId = userIdentityQueryService.containsOrg(userOrgList, org) ? org : loginUser.getOrgId();
        String tenantCode = userIdentityQueryService.containsTenant(tenantList, tenant) ? tenant : loginUser.getTenantCode();
        // 生成登录密钥 token
        Map<String, String> payload = new HashMap<>(5);
        payload.put("id", userId);
        payload.put("loginName", SecurityContext.getCurrentUser().getLoginName());
        payload.put("passWord", SecurityContext.getCurrentPassword());
        payload.put("orgId", orgId);
        payload.put("tenantCode", tenantCode);
        String token = JWTUtil.getToken(payload);
        // 用户信息
        LoginResult loginResult = LoginResult.formatLoginResult(loginUser);
        loginResult.setToken(token);
        return ApiResult.success(loginResult, "切换身份成功，请使用新令牌!");
    }

    @RequestMapping(value = "/info", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResult<?> getUserInfo() {
        return ApiResult.success(currentUserProfileService.getCurrentUserInfo(SecurityContext.getCurrentUser(), this.getToken()));
    }

    @RequestMapping(value = "/logout", method = RequestMethod.POST)
    public ApiResult<NullResult> logout() {
        User user = this.getUserByToken();
        if (user != null) {
            log.debug("User [{}] logout.", user.getLoginName());
        }
        return ApiResult.successNoResult();
    }

    private Boolean checkPsd(User loginUser, LoginParams loginParams) {
        return loginUser.getPassword().equals(EncryptUtil.encryptPassword(loginParams.getPassword(), loginUser.getSalt()));
    }

    @RequestMapping(value = "/avatar/{userId}", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResult<NullResult> uploadAvatar(@PathVariable() String userId, @RequestParam("file") MultipartFile file) {
        userAccountCommandService.uploadAvatar(resolveCurrentUserId(), userId, file);
        return ApiResult.successNoResult();
    }

    @RequestMapping(value = "/update/{userId}", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResult<NullResult> updateUserInfo(@PathVariable(required = true) String userId,
                                                 @Valid @RequestBody UpdateUserProfileRequest params) {
        userAccountCommandService.updateUserProfile(resolveCurrentUserId(), userId, params);
        return ApiResult.successNoResult();
    }

    /**
     * 获取当前用户的菜单
     * <p>
     * 根据提供的参数，查询并返回当前用户的菜单列表。
     *
     * @param params 包含查询参数的Map对象，参数包括flag、appId和tenantCode
     * @return 返回包含查询结果的ApiResult对象，如果查询成功则返回包含菜单列表的成功结果，否则返回失败结果
     * @throws Exception 如果在查询过程中发生异常，则抛出该异常
     */
    @RequestMapping(value = "/menu", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResult getCurrentUserMenu(@RequestBody(required = false) Map<String, Object> params) {
        Map<String, Object> safeParams = params == null ? Collections.emptyMap() : params;
        User user = getUserByToken();
        List<Map<String, Object>> menuItemList = userAuthorizationQueryService.getCurrentUserMenu(
                user,
                getToken(),
                (String) safeParams.get("flag"),
                (String) safeParams.get("appId"),
                (String) safeParams.get("tenantCode")
        );
        return ApiResult.success(menuItemList);
    }

    /**
     * 用于管理员重置密码
     * <p>
     * 通过该方法，管理员可以重置其密码。默认密码长度为8位，最长为32位。
     *
     * @param passwordLength 密码长度，默认为8位，最长为32位
     * @return 返回操作结果，包括密码重置是否成功及生成的随机密码
     * @throws Exception 如果在重置密码过程中发生异常，则抛出该异常
     */
    @RequestMapping(value = "/resetPassword", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResult resetPassword(@RequestParam(defaultValue = "8", required = false) int passwordLength) {
        return ApiResult.success(userAccountCommandService.resetCurrentUserPassword(resolveCurrentUserId(), passwordLength));
    }

    @RequestMapping(value = "/forgetValid", method = RequestMethod.POST)
    public ApiResult forgetValid(@Valid @RequestBody ForgetValidRequest form) {
        User foundUser = accountRecoveryService.findUserByValidBox(form);
        User result = new User();
        result.setId(foundUser.getId());
        return ApiResult.success(result);
    }

    @RequestMapping(value = "/forget", method = RequestMethod.POST)
    public ApiResult<NullResult> forgetPassword(@Valid @RequestBody ForgetPasswordRequest form) {
        accountRecoveryService.forgetPassword(form);
        return ApiResult.successNoResult();
    }

    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    public ApiResult<NullResult> validateUser(@Valid @RequestBody ValidateUserRequest form) {
        accountRecoveryService.validateUser(form);
        return ApiResult.successNoResult();
    }

    @RequestMapping(value = "/bindAccount", method = RequestMethod.POST)
    public ApiResult<NullResult> bindAccount(@Valid @RequestBody BindAccountRequest form) {
        accountRecoveryService.bindAccount(form);
        return ApiResult.successNoResult();
    }

    /**
     * 通过token获取用户信息
     * <p>
     * 根据当前会话的token获取对应的用户信息。
     *
     * @return 返回获取到的用户信息对象，如果未找到对应的用户则返回null
     */
    private User getUserByToken() {
        cn.geelato.security.User currentUser = SecurityContext.getCurrentUser();
        if (currentUser != null && StringUtils.isNotBlank(currentUser.getUserId())) {
            return dao.queryForObject(User.class, "id", currentUser.getUserId());
        }
        ShiroUser shiroUser = (ShiroUser) SecurityUtils.getSubject().getPrincipal();
        return shiroUser != null ? dao.queryForObject(User.class, "loginName", shiroUser.loginName) : null;
    }

    private String getToken() {
        return this.request.getHeader("Authorization");
    }


    private String resolveCurrentUserId() {
        cn.geelato.security.User currentUser = SecurityContext.getCurrentUser();
        return currentUser == null ? null : currentUser.getUserId();
    }
}
