package cn.geelato.web.platform.m.security.rest;

import cn.geelato.core.constants.MediaTypes;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.interceptor.annotation.IgnoreJWTVerify;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.security.entity.*;
import cn.geelato.web.platform.m.security.service.AccountService;
import cn.geelato.web.platform.m.security.service.OrgService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final AccountService accountService;
    private final OrgService orgService;

    @Autowired
    public SystemRestController(AccountService accountService, OrgService orgService) {
        this.accountService = accountService;
        this.orgService = orgService;
    }


    @IgnoreJWTVerify
    @RequestMapping(value = "/getRoleListByPage", method = RequestMethod.GET, produces = {MediaTypes.APPLICATION_JSON_UTF_8})
    public ApiPagedResult getAccountList(HttpServletRequest req) {
        // 初始化返回值
        ApiPagedResult apiPageResult = new ApiPagedResult<DataItems>();
        List mapList = dao.queryForMapList(Role.class);
        apiPageResult.setData(new DataItems(mapList, mapList.size()));
        apiPageResult.success();
        apiPageResult.setTotal(mapList.size());
        return apiPageResult;
    }

    @RequestMapping(value = "/getUserInfo", method = RequestMethod.GET)
    public ApiResult getUserInfo(HttpServletRequest req) {
        try {
            User user = this.getUserByToken(req);
            LoginResult loginResult = LoginResult.formatLoginResult(user);
            loginResult.setToken(this.getToken(req));
            loginResult.setHomePath("");
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
    public ApiResult<NullResult> logout(HttpServletRequest req) {
        try {
            User user = this.getUserByToken(req);
            log.debug("User [" + user.getLoginName() + "] logout.");
            return ApiResult.successNoResult();
        } catch (Exception e) {
            log.error("退出失败", e);
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
    public ApiResult resetPassword(HttpServletRequest req, @RequestParam(defaultValue = "8", required = false) int passwordLength) throws Exception {
        User user = this.getUserByToken(req);
        String plainPassword = RandomStringUtils.randomAlphanumeric(passwordLength > 32 ? 32 : passwordLength);
        user.setPlainPassword(plainPassword);
        accountService.entryptPassword(user);
        dao.save(user);
        return ApiResult.success(plainPassword);
    }


    /**
     * 通过token获取用户信息
     *
     * @param req
     * @return
     * @throws Exception
     */
    private User getUserByToken(HttpServletRequest req) throws Exception {
        return dao.queryForObject(User.class, "loginName", req.getAttribute("loginName"));
    }

    private String getToken(HttpServletRequest req) {
        return req.getHeader("authorization");
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
