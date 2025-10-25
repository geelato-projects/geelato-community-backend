package cn.geelato.web.platform.srv.security;

import cn.geelato.web.common.constants.MediaTypes;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.DataItems;
import cn.geelato.lang.api.NullResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.common.interceptor.annotation.IgnoreVerify;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.security.entity.LoginResult;
import cn.geelato.meta.Org;
import cn.geelato.meta.Role;
import cn.geelato.meta.User;
import cn.geelato.web.platform.srv.security.service.AccountService;
import cn.geelato.web.platform.srv.security.service.OrgService;
import cn.geelato.web.platform.utils.EncryptUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Created by hongxq on 2022/10/1
 */
@ApiRestController(value = "/sys")
@Slf4j
public class SystemRestController extends BaseController {
    private final OrgService orgService;

    @Autowired
    public SystemRestController(AccountService accountService, OrgService orgService) {
        this.orgService = orgService;
    }


    @IgnoreVerify
    @RequestMapping(value = "/getRoleListByPage", method = RequestMethod.GET, produces = {MediaTypes.APPLICATION_JSON_UTF_8})
    public ApiPagedResult getAccountList() {
        List mapList = dao.queryForMapList(Role.class);
        return ApiPagedResult.success(new DataItems<>(mapList, mapList.size()), 1L, mapList.size(), mapList.size(), mapList.size());
    }

    @RequestMapping(value = "/getUserInfo", method = RequestMethod.GET)
    public ApiResult getUserInfo() {
        try {
            User user = this.getUserByToken();
            LoginResult loginResult = LoginResult.formatLoginResult(user);
            loginResult.setToken(this.getToken());
            loginResult.setRoles(null);
            // 用户所属公司
            setCompany(loginResult);
            return ApiResult.success(loginResult);
        } catch (Exception e) {
            log.error("getUserInfo", e);
            return ApiResult.fail(e.getMessage());
        }
    }


    @RequestMapping(value = "/logout", method = RequestMethod.GET)
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
     * 用于管理员重置密码
     * <p>
     * 该方法通过接收密码长度参数（默认为8位，最长为32位），生成一个随机密码，并更新管理员的密码。
     *
     * @param passwordLength 密码长度，默认为8位，最长为32位
     * @return ApiResult对象，包含操作结果和生成的随机密码
     * @throws Exception 如果在操作过程中发生异常，则抛出该异常
     */
    @RequestMapping(value = "/resetPassword", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResult resetPassword(@RequestParam(defaultValue = "8", required = false) int passwordLength) throws Exception {
        User user = this.getUserByToken();
        String plainPassword = RandomStringUtils.randomAlphanumeric(passwordLength > 32 ? 32 : passwordLength);
        user.setPlainPassword(plainPassword);
        EncryptUtil.encryptPassword(user);
        dao.save(user);
        return ApiResult.success(plainPassword);
    }


    /**
     * 通过token获取用户信息
     * <p>
     * 从当前请求中获取登录名，并通过该登录名查询用户信息。
     *
     * @return 返回查询到的用户信息对象
     * @throws Exception 如果在查询过程中发生异常，则抛出该异常
     */
    private User getUserByToken() throws Exception {
        return dao.queryForObject(User.class, "loginName", this.request.getAttribute("loginName"));
    }

    private String getToken() {
        return this.request.getHeader("authorization");
    }

    /**
     * 设置用户所属公司信息
     * <p>
     * 根据登录结果中的公司ID或组织ID，设置登录结果中的公司名称和公司ID。
     *
     * @param loginResult 登录结果对象，包含用户登录后的相关信息
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
