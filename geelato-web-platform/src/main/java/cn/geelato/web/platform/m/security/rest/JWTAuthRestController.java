package cn.geelato.web.platform.m.security.rest;

import cn.geelato.web.platform.m.base.service.RuleService;
import cn.geelato.web.platform.m.base.service.UploadService;
import cn.geelato.web.platform.m.security.entity.*;
import cn.geelato.web.platform.m.security.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.platform.interceptor.annotation.IgnoreJWTVerify;
import cn.geelato.web.platform.enums.ValidTypeEnum;
import cn.geelato.web.platform.m.base.rest.BaseController;
import cn.geelato.web.platform.m.base.service.AttachService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Created by hongxq on 2022/5/1.
 */
@Controller
@RequestMapping(value = {"/api/user"})
public class JWTAuthRestController extends BaseController {

    private static final String ROOT_AVATAR_DIRECTORY = "upload/avatar";
    private static final String AVATAR_BASE64_PREFIX = "data:image/png;base64,";
    private final Logger logger = LoggerFactory.getLogger(JWTAuthRestController.class);
    @Autowired
    protected AccountService accountService;
    @Autowired
    protected AuthCodeService authCodeService;
    @Autowired
    protected OrgService orgService;
    @Autowired
    private UploadService uploadService;
    @Autowired
    private AttachService attachService;
    @Autowired
    private RuleService ruleService;

    @IgnoreJWTVerify
    @RequestMapping(value = "/login", method = RequestMethod.POST, produces = {"application/json;charset=UTF-8"})
    @ResponseBody
    public ApiResult login(@RequestBody LoginParams loginParams, HttpServletRequest req) {
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

                // TODO 将token 写入域名下的cookies

                apiResult.setData(loginResult);
            } else {
                return apiResult.error().setMsg("账号或密码不正确");
            }
        } catch (Exception exception) {
            logger.error(exception.getMessage(), exception);
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
    @ResponseBody
    public ApiResult getUserInfo(HttpServletRequest req) {
        try {
            User user = this.getUserByToken(req);
            if (user == null) {
                return new ApiResult().error().setMsg("获取用户失败");
            }

            LoginResult loginResult = LoginResult.formatLoginResult(user);
            loginResult.setToken(this.getToken(req));
            loginResult.setHomePath("");
            loginResult.setRoles(null);
            // 用户所属公司
            setCompany(loginResult);

            return new ApiResult().success().setData(loginResult);
        } catch (Exception e) {
            logger.error("getUserInfo", e);
            return new ApiResult().error().setMsg(e.getMessage());
        }
    }

    @RequestMapping(value = "/avatar/{userId}", method = {RequestMethod.POST, RequestMethod.GET})
    @ResponseBody
    public ApiResult uploadAvatar(@PathVariable(required = true) String userId, @RequestParam("file") MultipartFile file) {
        ApiResult result = new ApiResult();
        try {
            // 用户信息
            if (Strings.isBlank(userId)) {
                return result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
            }
            User user = dao.queryForObject(User.class, userId);
            Assert.notNull(user, ApiErrorMsg.IS_NULL);
            // 头像
            if (file == null || file.isEmpty()) {
                return result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
            }
            // 存入附件表
            // Attach attach = new Attach(file);
            // attach.setPath(uploadService.getSavePath(ROOT_AVATAR_DIRECTORY, attach.getName(), true));
            // byte[] bytes = file.getBytes();
            // Files.write(Paths.get(attach.getPath()), bytes);
            // Map<String, Object> attachMap = attachService.createModel(attach);
            // Base64，存数据库
            byte[] fileBytes = file.getBytes();
            String base64String = Base64.getEncoder().encodeToString(fileBytes);
            user.setAvatar(AVATAR_BASE64_PREFIX + base64String);
            dao.save(user);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/update/{userId}", method = {RequestMethod.POST, RequestMethod.GET})
    @ResponseBody
    public ApiResult updateUserInfo(@PathVariable(required = true) String userId, @RequestBody Map<String, Object> params) {
        ApiResult result = new ApiResult();
        try {
            // 用户信息
            if (Strings.isBlank(userId)) {
                return result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
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
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        }

        return result;
    }


    @RequestMapping(value = "/logout", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult logout(HttpServletRequest req) {
        try {
            User user = this.getUserByToken(req);
            logger.debug("User [" + user.getLoginName() + "] logout.");
            return new ApiResult();
        } catch (Exception e) {
            logger.error("退出失败", e);
            return new ApiResult().error();
        }
    }

    /**
     * 获取当前用户的菜单
     *
     * @return
     */
    @RequestMapping(value = "/menu", method = {RequestMethod.POST, RequestMethod.GET})
    @ResponseBody
    public ApiResult getCurrentUserMenu(@RequestBody Map<String, Object> params, HttpServletRequest request) throws Exception {
        ApiResult result = new ApiResult();
        List<Map<String, Object>> menuItemList = new ArrayList<>();
        result.setData(menuItemList);
        // post参数
        Map map = new HashMap<>();
        String flag = (String) params.get("flag");
        String appId = (String) params.get("appId");
        String tenantCode = (String) params.get("tenantCode");
        // 用户
        User user = getUserByToken(request);
        logger.info(String.format("当前用户菜单查询，用户：%s", (user != null ? String.format("%s（%s）", user.getName(), user.getLoginName()) : "")));
        String token = getToken(request);
        logger.info(String.format("当前用户菜单查询，Token：%s", token));
        if (user == null || Strings.isBlank(token)) {
            return result;
        }
        // 用户与租户比对
        if (Strings.isNotBlank(tenantCode) && !tenantCode.equalsIgnoreCase(user.getTenantCode())) {
            logger.info(String.format("当前用户菜单查询，租户不一致：User=>%s | %s", user.getTenantCode(), tenantCode));
            return result;
        } else {
            tenantCode = user.getTenantCode();
        }
        logger.info(String.format("当前用户菜单查询，租户：%s；应用：%s", tenantCode, appId));
        // 菜单查询
        if (Strings.isNotBlank(appId) && Strings.isNotBlank(tenantCode)) {
            map.put("currentUser", user.getId());
            map.put("appId", appId);
            map.put("tenantCode", tenantCode);
            map.put("flag", flag);
            menuItemList = dao.queryForMapList("select_platform_tree_node_app_page", map);
        }

        return result.setData(menuItemList);
    }

    /**
     * 用于管理员重置密码
     *
     * @param passwordLength 默认为8位，最长为32位
     * @return
     */
    @RequestMapping(value = "/resetPassword", method = {RequestMethod.POST, RequestMethod.GET})
    @ResponseBody
    public ApiResult resetPassword(HttpServletRequest req, @RequestParam(defaultValue = "8", required = false) int passwordLength) throws Exception {
        User user = this.getUserByToken(req);
        String plainPassword = RandomStringUtils.randomAlphanumeric(passwordLength > 32 ? 32 : passwordLength);
        user.setPlainPassword(plainPassword);
        accountService.entryptPassword(user);
        dao.save(user);
        return new ApiResult().setData(plainPassword);
    }

    @RequestMapping(value = "/forgetValid", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult forgetValid(@RequestBody Map<String, Object> params) {
        ApiResult result = new ApiResult();
        try {
            ForgetPasswordParams form = new ForgetPasswordParams();
            BeanUtils.populate(form, params);
            Map<String, Object> map = new HashMap<>();
            String validLabel = ValidTypeEnum.getLabel(form.getValidType());
            if (Strings.isBlank(form.getValidBox()) || Strings.isBlank(validLabel)) {
                return result.error();
            }
            map.put(validLabel, form.getValidBox());
            if (ValidTypeEnum.MOBILE.getValue().equals(form.getValidType())) {
                if (Strings.isBlank(form.getPrefix())) {
                    return result.error();
                }
                map.put("mobilePrefix", form.getPrefix());
            }
            List<User> users = dao.queryList(User.class, map, null);
            if (users != null && users.size() == 1) {
                User user = new User();
                user.setId(users.get(0).getId());
                return result.success().setData(user);
            }
            result.error();
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.UPDATE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/forget", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult forgetPassword(@RequestBody Map<String, Object> params) {
        ApiResult result = new ApiResult();
        try {
            ForgetPasswordParams form = new ForgetPasswordParams();
            BeanUtils.populate(form, params);
            // 用户、密码
            if (Strings.isBlank(form.getUserId()) || Strings.isBlank(form.getPassword())) {
                return result.error().setMsg(ApiErrorMsg.PARAMETER_MISSING);
            }
            // 验证码
            AuthCodeParams code = AuthCodeParams.buildAuthCodeParams(form);
            if (!authCodeService.validate(code)) {
                return result.error().setMsg(ApiErrorMsg.AUTH_CODE_ERROR);
            }
            // 修改密码
            User user = dao.queryForObject(User.class, form.getUserId());
            Assert.notNull(user, ApiErrorMsg.IS_NULL);
            user.setPlainPassword(form.getPassword());
            accountService.entryptPassword(user);
            dao.save(user);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.UPDATE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult validateUser(@RequestBody Map<String, Object> params) {
        ApiResult result = new ApiResult();
        try {
            AuthCodeParams form = new AuthCodeParams();
            BeanUtils.populate(form, params);
            // 用户、密码
            if (Strings.isBlank(form.getValidType()) || Strings.isBlank(form.getUserId()) || Strings.isBlank(form.getAuthCode())) {
                return result.error().setMsg(ApiErrorMsg.PARAMETER_MISSING);
            }
            // 用户验证
            User user = dao.queryForObject(User.class, form.getUserId());
            Assert.notNull(user, ApiErrorMsg.IS_NULL);
            // 验证方式：密码、手机、邮箱
            if (ValidTypeEnum.PASSWORD.getValue().equals(form.getValidType())) {
                if (Strings.isNotBlank(user.getPassword()) && Strings.isNotBlank(user.getSalt())) {
                    String pwd = accountService.entryptPassword(form.getAuthCode(), user.getSalt());
                    if (user.getPassword().equals(pwd)) {
                        return result.success();
                    }
                }
                return result.error().setMsg(ApiErrorMsg.VALIDATE_USER_PASSWORD);
            } else if (ValidTypeEnum.MOBILE.getValue().equals(form.getValidType())) {
                // action、userId、validType、authCode
                if (authCodeService.validate(form)) {
                    return result.success();
                }
                return result.error().setMsg(ApiErrorMsg.VALIDATE_USER_MOBILE);
            } else if (ValidTypeEnum.MAIL.getValue().equals(form.getValidType())) {
                if (authCodeService.validate(form)) {
                    return result.success();
                }
                return result.error().setMsg(ApiErrorMsg.VALIDATE_USER_EMAIL);
            }
            return result.error().setMsg(ApiErrorMsg.VALIDATE_USER);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.UPDATE_FAIL);
        }

        return result;
    }

    @RequestMapping(value = "/bindAccount", method = RequestMethod.POST)
    @ResponseBody
    public ApiResult bindAccount(@RequestBody Map<String, Object> params) {
        ApiResult result = new ApiResult();
        try {
            AuthCodeParams form = new AuthCodeParams();
            BeanUtils.populate(form, params);
            // 用户、密码
            if (Strings.isBlank(form.getValidType()) || Strings.isBlank(form.getUserId()) || Strings.isBlank(form.getAuthCode()) || Strings.isBlank(form.getValidBox())) {
                return result.error().setMsg(ApiErrorMsg.PARAMETER_MISSING);
            }
            // 用户验证
            User user = dao.queryForObject(User.class, form.getUserId());
            Assert.notNull(user, ApiErrorMsg.IS_NULL);
            // 账号绑定
            if (ValidTypeEnum.PASSWORD.getValue().equals(form.getValidType())) {
                user.setPlainPassword(form.getValidBox());
                accountService.entryptPassword(user);
                dao.save(user);
                return result.success();
            } else if (ValidTypeEnum.MOBILE.getValue().equals(form.getValidType())) {
                if (authCodeService.validate(form)) {
                    user.setMobilePhone(form.getValidBox());
                    user.setMobilePrefix(form.getPrefix());
                    dao.save(user);
                    return result.success();
                } else {
                    return result.error().setMsg(ApiErrorMsg.AUTH_CODE_ERROR);
                }
            } else if (ValidTypeEnum.MAIL.getValue().equals(form.getValidType())) {
                if (authCodeService.validate(form)) {
                    user.setEmail(form.getValidBox());
                    dao.save(user);
                    return result.success();
                } else {
                    return result.error().setMsg(ApiErrorMsg.AUTH_CODE_ERROR);
                }
            }
            return result.error().setMsg(ApiErrorMsg.OPERATE_FAIL);
        } catch (Exception e) {
            logger.error(e.getMessage());
            result.error().setMsg(ApiErrorMsg.UPDATE_FAIL);
        }

        return result;
    }

    /**
     * 通过token获取用户信息
     *
     * @param req
     * @return
     * @throws Exception
     */
    private User getUserByToken(HttpServletRequest req) throws Exception {
        ShiroDbRealm.ShiroUser shiroUser = SecurityHelper.getCurrentUser();
        User user = null;
        if (shiroUser != null) {
            user = dao.queryForObject(User.class, "loginName", shiroUser.loginName);
        }
        return user;
    }

    private String getToken(HttpServletRequest req) {
        return req.getHeader("Authorization");
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
