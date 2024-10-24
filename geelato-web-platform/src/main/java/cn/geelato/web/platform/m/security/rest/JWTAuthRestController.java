package cn.geelato.web.platform.m.security.rest;

import cn.geelato.core.constants.MediaTypes;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.interceptor.annotation.IgnoreJWTVerify;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.base.service.AttachService;
import cn.geelato.web.platform.m.base.service.UploadService;
import cn.geelato.web.platform.m.security.entity.*;
import cn.geelato.web.platform.m.security.enums.ValidTypeEnum;
import cn.geelato.web.platform.m.security.service.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Created by hongxq on 2022/5/1.
 */
@ApiRestController("/user")
@Slf4j
public class JWTAuthRestController extends BaseController {
    protected AccountService accountService;
    protected AuthCodeService authCodeService;
    protected OrgService orgService;
    private final UploadService uploadService;
    private final AttachService attachService;

    @Autowired
    public JWTAuthRestController(AccountService accountService,
                                 AuthCodeService authCodeService,
                                 OrgService orgService,
                                 UploadService uploadService,
                                 AttachService attachService) {
        this.accountService = accountService;
        this.authCodeService = authCodeService;
        this.orgService = orgService;
        this.uploadService = uploadService;
        this.attachService = attachService;
    }

    @IgnoreJWTVerify
    @RequestMapping(value = "/login", method = RequestMethod.POST, produces = {MediaTypes.APPLICATION_JSON_UTF_8})
    public ApiResult login(@RequestBody LoginParams loginParams) {
        ApiResult apiResult = new ApiResult();
        try {
            // 用户登录校验
            User loginUser = dao.queryForObject(User.class, "loginName", loginParams.getUsername());
            Boolean checkPsdRst = CheckPsd(loginUser, loginParams);
            if (loginUser != null && checkPsdRst) {
                apiResult.success();
                apiResult.setMsg("认证成功！");
                apiResult.setCode(20000);

                String userId = loginUser.getId();

                Map<String, String> payload = new HashMap<>(3);
                payload.put("id", userId);
                payload.put("loginName", loginUser.getLoginName());
                payload.put("passWord", loginParams.getPassword());
                String token = JWTUtil.getToken(payload);

                LoginResult loginResult = LoginResult.formatLoginResult(loginUser);
                loginResult.setToken(token);
                loginResult.setHomePath("");
                loginResult.setRoles(getRoles(userId));
                // 用户所属公司
                setCompany(loginResult);
                apiResult.setData(loginResult);
            } else {
                return apiResult.error().setMsg("账号或密码不正确");
            }
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
            apiResult.error().setMsg("账号或密码不正确");
        }
        return apiResult;
    }

    private Boolean CheckPsd(User loginUser, LoginParams loginParams) {
        return loginUser.getPassword().equals(accountService.entryptPassword(loginParams.getPassword(), loginUser.getSalt()));
    }

    private ArrayList<LoginRoleInfo> getRoles(String id) {
        ArrayList<LoginRoleInfo> roles = new ArrayList<>();
        roles.add(new LoginRoleInfo("Super Admin", "super"));
        return roles;
    }

    private String getAvatar(String id) {
        return "https://q1.qlogo.cn/g?b=qq&nk=339449197&s=640";
    }

    @RequestMapping(value = "/info", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResult getUserInfo() {
        try {
            User user = this.getUserByToken();
            if (user == null) {
                return new ApiResult().error().setMsg("获取用户失败");
            }

            LoginResult loginResult = LoginResult.formatLoginResult(user);
            loginResult.setToken(this.getToken());
            loginResult.setHomePath("");
            loginResult.setRoles(null);
            // 用户所属公司
            setCompany(loginResult);

            return new ApiResult().success().setData(loginResult);
        } catch (Exception e) {
            log.error("getUserInfo", e);
            return new ApiResult().error().setMsg(e.getMessage());
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
            // 存入附件表
            // Attach attach = new Attach(file);
            // attach.setPath(uploadService.getSavePath(UploadService.ROOT_AVATAR_DIRECTORY, attach.getName(), true));
            // byte[] bytes = file.getBytes();
            // Files.write(Paths.get(attach.getPath()), bytes);
            // Map<String, Object> attachMap = attachService.createModel(attach);
            // Base64，存数据库
            byte[] fileBytes = file.getBytes();
            String base64String = Base64.getEncoder().encodeToString(fileBytes);
            user.setAvatar(UploadService.AVATAR_BASE64_PREFIX + base64String);
            dao.save(user);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage());
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
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }


    @RequestMapping(value = "/logout", method = RequestMethod.POST)
    public ApiResult<NullResult> logout() {
        try {
            User user = this.getUserByToken();
            log.debug("User [" + user.getLoginName() + "] logout.");
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error("退出失败", e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /**
     * 获取当前用户的菜单
     *
     * @return
     */
    @RequestMapping(value = "/menu", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResult getCurrentUserMenu(@RequestBody Map<String, Object> params) throws Exception {
        try {
            List<Map<String, Object>> menuItemList = new ArrayList<>();
            // post参数
            Map map = new HashMap<>();
            String flag = (String) params.get("flag");
            String appId = (String) params.get("appId");
            String tenantCode = (String) params.get("tenantCode");
            // 用户
            User user = getUserByToken();
            // log.info(String.format("当前用户菜单查询，用户：%s", (user != null ? String.format("%s（%s）", user.getName(), user.getLoginName()) : "")));
            String token = getToken();
            // log.info(String.format("当前用户菜单查询，Token：%s", token));
            if (user == null || Strings.isBlank(token)) {
                return ApiResult.fail("User or token is null");
            }
            // 用户与租户比对
            if (Strings.isNotBlank(tenantCode) && !tenantCode.equalsIgnoreCase(user.getTenantCode())) {
                // log.info(String.format("当前用户菜单查询，租户不一致：User=>%s | %s", user.getTenantCode(), tenantCode));
                return ApiResult.fail("user tenant code not equal");
            } else {
                tenantCode = user.getTenantCode();
            }
            // log.info(String.format("当前用户菜单查询，租户：%s；应用：%s", tenantCode, appId));
            // 菜单查询
            if (Strings.isNotBlank(appId) && Strings.isNotBlank(tenantCode)) {
                map.put("currentUser", user.getId());
                map.put("appId", appId);
                map.put("tenantCode", tenantCode);
                map.put("flag", flag);
                menuItemList = dao.queryForMapList("select_platform_tree_node_app_page", map);
            }
            return ApiResult.success(menuItemList);
        } catch (Exception e) {
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    /**
     * 用于管理员重置密码
     *
     * @param passwordLength 默认为8位，最长为32位
     * @return
     */
    @RequestMapping(value = "/resetPassword", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResult resetPassword(@RequestParam(defaultValue = "8", required = false) int passwordLength) throws Exception {
        try {
            User user = this.getUserByToken();
            String plainPassword = RandomStringUtils.randomAlphanumeric(passwordLength > 32 ? 32 : passwordLength);
            user.setPlainPassword(plainPassword);
            accountService.entryptPassword(user);
            dao.save(user);
            return ApiResult.success(plainPassword);
        } catch (Exception e) {
            log.error(e.getMessage());
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
            log.error(e.getMessage());
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
            accountService.entryptPassword(user);
            dao.save(user);
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error(e.getMessage());
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
                    String pwd = accountService.entryptPassword(form.getAuthCode(), user.getSalt());
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
            log.error(e.getMessage());
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
                accountService.entryptPassword(user);
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
            log.error(e.getMessage());
            return ApiResult.fail(e.getMessage());
        }
    }

    /**
     * 通过token获取用户信息
     *
     * @return
     * @throws Exception
     */
    private User getUserByToken() throws Exception {
        ShiroDbRealm.ShiroUser shiroUser = SecurityHelper.getCurrentUser();
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
     * 用户所属公司
     *
     * @param loginResult
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
}
