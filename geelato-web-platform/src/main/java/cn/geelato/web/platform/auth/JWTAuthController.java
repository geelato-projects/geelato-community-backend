package cn.geelato.web.platform.auth;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.UserOrg;
import cn.geelato.utils.Base64Utils;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.common.constants.MediaTypes;
import cn.geelato.web.common.interceptor.annotation.IgnoreVerify;
import cn.geelato.web.common.security.Org;
import cn.geelato.web.common.security.User;
import cn.geelato.web.common.shiro.ShiroUser;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.security.entity.*;
import cn.geelato.web.platform.m.security.enums.ValidTypeEnum;
import cn.geelato.web.platform.m.security.service.AuthCodeService;
import cn.geelato.web.platform.m.security.service.JWTUtil;
import cn.geelato.web.platform.m.security.service.OrgService;
import cn.geelato.web.platform.m.security.service.SecurityHelper;
import cn.geelato.web.platform.utils.EncryptUtil;
import com.alibaba.fastjson2.JSON;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApiRestController("/user")
@Slf4j
public class JWTAuthController extends BaseController {
    protected AuthCodeService authCodeService;
    protected OrgService orgService;

    @Autowired
    public JWTAuthController(AuthCodeService authCodeService, OrgService orgService) {
        this.authCodeService = authCodeService;
        this.orgService = orgService;
    }

    @IgnoreVerify
    @RequestMapping(value = "/login", method = RequestMethod.POST, produces = {MediaTypes.APPLICATION_JSON_UTF_8})
    public ApiResult<LoginResult> login(@RequestBody LoginParams loginParams) {
        try {
            // 用户登录校验
            User loginUser = dao.queryForObject(User.class, "loginName", loginParams.getUsername());
            Boolean checkPsdRst = checkPsd(loginUser, loginParams);
            if (checkPsdRst) {
                String userId = loginUser.getId();

                List<Tenant> tenantList= queryTenantList(userId);
                List<UserOrg> userOrgList= queryOrgList(userId);

                String orgId= checkOrg(userOrgList,loginParams.getOrg())?loginParams.getOrg():loginUser.getOrgId();
                String tenantCodeParam;
                if(StringUtils.isEmpty(loginParams.getSuffix())){
                    tenantCodeParam=loginParams.getTenant().replace("@","");
                }else {
                    tenantCodeParam = loginParams.getTenant();
                }
                String tenantCode=checkTenant(tenantList,tenantCodeParam)?tenantCodeParam:loginUser.getTenantCode();
                Map<String, String> payload = new HashMap<>(5);
                payload.put("id", userId);
                payload.put("loginName", loginUser.getLoginName());
                payload.put("passWord", loginParams.getPassword());
                payload.put("orgId", orgId);
                payload.put("tenantCode", tenantCode);
                String token = JWTUtil.getToken(payload);

                LoginResult loginResult = LoginResult.formatLoginResult(loginUser);
                loginResult.setToken(token);
                setCompany(loginResult);
                // 用户角色
                loginResult.setRoleIds(getRoleIdsByUserId(loginUser.getId(), null, loginUser.getTenantCode()));
                loginResult.setTenants(tenantList);
                loginResult.setOrgs(userOrgList);
                return ApiResult.success(loginResult, "认证成功!");
            } else {
                return ApiResult.fail("账号或密码不正确");
            }
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
            return ApiResult.fail("账号或密码不正确!");
        }
    }
    @SneakyThrows
    @RequestMapping(value = "/switchIdentity", method = RequestMethod.GET, produces = {MediaTypes.APPLICATION_JSON_UTF_8})
    public ApiResult<LoginResult> switchIdentity(String org,String tenant) {
        String userId = SecurityContext.getCurrentUser().getUserId();
        User loginUser = dao.queryForObject(User.class, "id", userId);
        List<Tenant> tenantList = queryTenantList(userId);
        List<UserOrg> userOrgList = queryOrgList(userId);

        String orgId = checkOrg(userOrgList, org) ? org :loginUser.getOrgId();
        String tenantCode = checkTenant(tenantList,tenant ) ?tenant: loginUser.getTenantCode();

        Map<String, String> payload = new HashMap<>(5);
        payload.put("id", userId);
        payload.put("loginName", SecurityContext.getCurrentUser().getLoginName());
        payload.put("passWord", SecurityContext.getCurrentPassword());
        payload.put("orgId", orgId);
        payload.put("tenantCode", tenantCode);
        String token = JWTUtil.getToken(payload);

        LoginResult loginResult = LoginResult.formatLoginResult(loginUser);
        loginResult.setToken(token);

        setCompany(loginResult);
        loginResult.setRoleIds(getRoleIdsByUserId(loginUser.getId(), null, loginUser.getTenantCode()));
        loginResult.setTenants(tenantList);
        loginResult.setOrgs(userOrgList);
        return ApiResult.success(loginResult, "切换身份成功，请使用新令牌!");
    }
    private @NotNull List<UserOrg> queryOrgList(String userId) {
        return dao.getJdbcTemplate().query(
                "select o.id, o.name, oru.default_org as defaultOrg, o.pid as pid, o.tenant_code as tenantCode " +
                        "from platform_org_r_user oru " +
                        "left join platform_org o on oru.org_id = o.id " +
                        "where oru.user_id = ?",
                (rs, rowNum) -> {
                    UserOrg userOrg = new UserOrg();
                    userOrg.setId(rs.getString("id"));
                    userOrg.setName(rs.getString("name"));
                    userOrg.setDefaultOrg(rs.getBoolean("defaultOrg"));
                    userOrg.setPid(rs.getString("pid"));
                    userOrg.setTenantCode(rs.getString("tenantCode"));
                    return userOrg;
                },
                userId
        );
    }

    private @NotNull List<Tenant> queryTenantList(String userId) {
        return dao.getJdbcTemplate().query("select t.`code` as code ,t.company_name as name from platform_user u left join platform_tenant t on u.tenant_code=t.tenant_code\n" +
                "where u.id=?", (rs, rowNum) -> new Tenant(rs.getString("code"),
                rs.getString("name")), userId);
    }

    private Boolean checkTenant(List<Tenant> tenantList, String tenantCode) {
        return tenantList.stream()
                .map(Tenant::getCode)
                .anyMatch(code -> code.equals(tenantCode));
    }

    private Boolean checkOrg(List<UserOrg> userOrgList, String  orgId) {
        return userOrgList.stream()
                .map(UserOrg::getId)
                .anyMatch(id -> id.equals(orgId));
    }

    @RequestMapping(value = "/logout", method = RequestMethod.POST)
    public ApiResult<NullResult> logout() {
        try {
            User user = this.getUserByToken();
            log.debug("User [{}] logout.", user.getLoginName());
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error("退出失败", e);
            return ApiResult.fail(e.getMessage());
        }
    }

    private Boolean checkPsd(User loginUser, LoginParams loginParams) {
        return loginUser.getPassword().equals(EncryptUtil.encryptPassword(loginParams.getPassword(), loginUser.getSalt()));
    }


    @RequestMapping(value = "/info", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResult getUserInfo() {
        try {
            User user = this.getUserByToken();
            if (user == null) {
                return ApiResult.fail("获取用户失败");
            }

            LoginResult loginResult = LoginResult.formatLoginResult(user);
            loginResult.setToken(this.getToken());
            loginResult.setRoles(null);
            // 用户所属公司
            setCompany(loginResult);
            // 用户角色
            loginResult.setRoleIds(getRoleIdsByUserId(user.getId(), null, user.getTenantCode()));

            return ApiResult.success(loginResult);
        } catch (Exception e) {
            log.error("getUserInfo", e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/avatar/{userId}", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResult<NullResult> uploadAvatar(@PathVariable(required = true) String userId, @RequestParam("file") MultipartFile file) {
        try {
            // 用户信息
            if (Strings.isBlank(userId)) {
                throw new RuntimeException("userId is null");
            }
            User user = dao.queryForObject(User.class, userId);
            Assert.notNull(user, ApiErrorMsg.IS_NULL);
            // 头像
            if (file == null || file.isEmpty()) {
                throw new RuntimeException("Avatar file is null");
            }
            user.setAvatar(Base64Utils.fromFile(file.getBytes(), "image/png"));
            dao.save(user);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/update/{userId}", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResult<NullResult> updateUserInfo(@PathVariable(required = true) String userId, @RequestBody Map<String, Object> params) {
        try {
            // 用户信息
            if (Strings.isBlank(userId)) {
                throw new RuntimeException("userId is null");
            }
            User user = dao.queryForObject(User.class, userId);
            Assert.notNull(user, ApiErrorMsg.IS_NULL);
            // 基础信息更新
            for (Map.Entry<String, Object> param : params.entrySet()) {
                String key = param.getKey();
                Object value = param.getValue();
                Class<?> clazz = user.getClass();
                Field labelField = clazz.getDeclaredField(key);
                labelField.setAccessible(true);
                labelField.set(user, value);
            }
            dao.save(user);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
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
    public ApiResult getCurrentUserMenu(@RequestBody Map<String, Object> params) throws Exception {
        try {
            List<Map<String, Object>> menuItemList = new ArrayList<>();
            Map map = new HashMap<>();
            String flag = (String) params.get("flag");
            String appId = (String) params.get("appId");
            String tenantCode = (String) params.get("tenantCode");
            User user = getUserByToken();
            String token = getToken();
            if (user == null || Strings.isBlank(token)) {
                return ApiResult.fail("User or token is null");
            }
            if (Strings.isNotBlank(tenantCode) && !tenantCode.equalsIgnoreCase(user.getTenantCode())) {
                return ApiResult.fail("user tenant code not equal");
            } else {
                tenantCode = user.getTenantCode();
            }
            if (Strings.isNotBlank(appId) && Strings.isNotBlank(tenantCode)) {
                map.put("currentUser", user.getId());
                map.put("appId", appId);
                map.put("tenantCode", tenantCode);
                map.put("flag", flag);
                menuItemList = dao.queryForMapList("select_platform_tree_node_app_page", map);
            }
            return ApiResult.success(menuItemList);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
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
    public ApiResult resetPassword(@RequestParam(defaultValue = "8", required = false) int passwordLength) throws Exception {
        try {
            User user = this.getUserByToken();
            String plainPassword = RandomStringUtils.randomAlphanumeric(passwordLength > 32 ? 32 : passwordLength);
            user.setPlainPassword(plainPassword);
            EncryptUtil.encryptPassword(user);
            dao.save(user);
            return ApiResult.success(plainPassword);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/forgetValid", method = RequestMethod.POST)
    public ApiResult forgetValid(@RequestBody Map<String, Object> params) {
        try {
            ForgetPasswordParams form = new ForgetPasswordParams();
            BeanUtils.populate(form, params);
            Map<String, Object> map = new HashMap<>();
            String validLabel = ValidTypeEnum.getLabel(form.getValidType());
            if (Strings.isBlank(form.getValidBox()) || Strings.isBlank(validLabel)) {
                throw new Exception("Parameter error");
            }
            map.put(validLabel, form.getValidBox());
            if (ValidTypeEnum.MOBILE.getValue().equals(form.getValidType())) {
                if (Strings.isBlank(form.getPrefix())) {
                    throw new Exception("Parameter error");
                }
                map.put("mobilePrefix", form.getPrefix());
            }
            List<User> users = dao.queryList(User.class, map, null);
            if (users != null && users.size() == 1) {
                User user = new User();
                user.setId(users.get(0).getId());
                return ApiResult.success(user);
            } else {
                throw new RuntimeException("user not found");
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/forget", method = RequestMethod.POST)
    public ApiResult<NullResult> forgetPassword(@RequestBody Map<String, Object> params) {
        try {
            ForgetPasswordParams form = new ForgetPasswordParams();
            BeanUtils.populate(form, params);
            // 用户、密码
            if (Strings.isBlank(form.getUserId()) || Strings.isBlank(form.getPassword())) {
                throw new Exception("User or password is null");
            }
            // 验证码
            AuthCodeParams code = AuthCodeParams.buildAuthCodeParams(form);
            if (!authCodeService.validate(code)) {
                throw new Exception("验证码错误");
            }
            // 修改密码
            User user = dao.queryForObject(User.class, form.getUserId());
            Assert.notNull(user, ApiErrorMsg.IS_NULL);
            user.setPlainPassword(form.getPassword());
            EncryptUtil.encryptPassword(user);
            dao.save(user);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    public ApiResult<NullResult> validateUser(@RequestBody Map<String, Object> params) {
        try {
            AuthCodeParams form = new AuthCodeParams();
            BeanUtils.populate(form, params);
            // 用户、密码
            if (Strings.isBlank(form.getValidType()) || Strings.isBlank(form.getUserId()) || Strings.isBlank(form.getAuthCode())) {
                throw new RuntimeException("Parameter error");
            }
            // 用户验证
            User user = dao.queryForObject(User.class, form.getUserId());
            Assert.notNull(user, ApiErrorMsg.IS_NULL);
            // 验证方式：密码、手机、邮箱
            if (ValidTypeEnum.PASSWORD.getValue().equals(form.getValidType())) {
                if (Strings.isNotBlank(user.getPassword()) && Strings.isNotBlank(user.getSalt())) {
                    String pwd = EncryptUtil.encryptPassword(form.getAuthCode(), user.getSalt());
                    if (user.getPassword().equals(pwd)) {
                        return ApiResult.successNoResult();
                    }
                }
                throw new RuntimeException("账号密码验证失败");
            } else if (ValidTypeEnum.MOBILE.getValue().equals(form.getValidType())) {
                // action、userId、validType、authCode
                if (authCodeService.validate(form)) {
                    return ApiResult.successNoResult();
                }
                throw new RuntimeException("手机号码验证失败");
            } else if (ValidTypeEnum.MAIL.getValue().equals(form.getValidType())) {
                if (authCodeService.validate(form)) {
                    return ApiResult.successNoResult();
                }
                throw new RuntimeException("电子邮箱验证失败");
            }
            throw new RuntimeException("验证失败");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    @RequestMapping(value = "/bindAccount", method = RequestMethod.POST)
    public ApiResult<NullResult> bindAccount(@RequestBody Map<String, Object> params) {
        try {
            AuthCodeParams form = new AuthCodeParams();
            BeanUtils.populate(form, params);
            // 用户、密码
            if (Strings.isBlank(form.getValidType()) || Strings.isBlank(form.getUserId()) || Strings.isBlank(form.getAuthCode()) || Strings.isBlank(form.getValidBox())) {
                throw new RuntimeException("Parameter error");
            }
            // 用户验证
            User user = dao.queryForObject(User.class, form.getUserId());
            Assert.notNull(user, ApiErrorMsg.IS_NULL);
            // 账号绑定
            if (ValidTypeEnum.PASSWORD.getValue().equals(form.getValidType())) {
                user.setPlainPassword(form.getValidBox());
                EncryptUtil.encryptPassword(user);
                dao.save(user);
                return ApiResult.successNoResult();
            } else if (ValidTypeEnum.MOBILE.getValue().equals(form.getValidType())) {
                if (authCodeService.validate(form)) {
                    user.setMobilePhone(form.getValidBox());
                    user.setMobilePrefix(form.getPrefix());
                    dao.save(user);
                    return ApiResult.successNoResult();
                }
                throw new RuntimeException("验证码错误");
            } else if (ValidTypeEnum.MAIL.getValue().equals(form.getValidType())) {
                if (authCodeService.validate(form)) {
                    user.setEmail(form.getValidBox());
                    dao.save(user);
                    return ApiResult.successNoResult();
                }
                throw new RuntimeException("验证码错误");
            }
            throw new RuntimeException("绑定失败");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /**
     * 通过token获取用户信息
     * <p>
     * 根据当前会话的token获取对应的用户信息。
     *
     * @return 返回获取到的用户信息对象，如果未找到对应的用户则返回null
     * @throws Exception 如果在获取用户信息的过程中发生异常，则抛出该异常
     */
    private User getUserByToken() throws Exception {
        ShiroUser shiroUser = SecurityHelper.getCurrentUser();
        User user = null;
        if (shiroUser != null) {
            user = dao.queryForObject(User.class, "loginName", shiroUser.loginName);
        }
        return user;
    }

    private String getToken() {
        return this.request.getHeader("Authorization");
    }

    /**
     * 设置用户所属公司
     * <p>
     * 根据登录结果中的公司ID或组织ID，设置登录结果中的公司名称和公司ID。
     *
     * @param loginResult 登录结果对象，包含用户的登录信息
     */
    private void setCompany(LoginResult loginResult) {
        if (Strings.isNotBlank(loginResult.getCompanyId())) {
            Org org = orgService.getModel(Org.class, loginResult.getCompanyId());
            loginResult.setCompanyName(org.getName());
        } else if (Strings.isNotBlank(loginResult.getOrgId())) {
            Org org = orgService.getCompany(loginResult.getOrgId());
            if (org != null) {
                loginResult.setCompanyId(org.getId());
                loginResult.setCompanyName(org.getName());
            }
        }
    }

    /**
     * 获取用户的角色信息
     *
     * @param userId     用户ID
     * @param appId      应用ID
     * @param tenantCode 租户代码
     */
    private List<Role> getRolesByUserId(String userId, String appId, String tenantCode) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("appId", appId);
        params.put("tenantCode", tenantCode);
        List<Map<String, Object>> mapList = dao.queryForMapList("page_query_platform_role_by_user_id", params);
        return JSON.parseArray(JSON.toJSONString(mapList), Role.class);
    }

    private String getRoleIdsByUserId(String userId, String appId, String tenantCode) {
        List<Role> roles = getRolesByUserId(userId, appId, tenantCode);
        if (roles != null && !roles.isEmpty()) {
            return roles.stream().map(Role::getId).reduce((a, b) -> a + "," + b).get();
        }
        return null;
    }
}
