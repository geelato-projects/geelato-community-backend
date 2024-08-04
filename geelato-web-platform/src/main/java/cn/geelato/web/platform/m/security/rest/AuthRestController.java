package cn.geelato.web.platform.m.security.rest;

import cn.geelato.web.platform.m.security.entity.User;
import cn.geelato.web.platform.m.security.service.AccountService;
import cn.geelato.web.platform.m.security.service.SecurityHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.*;
import org.apache.shiro.subject.Subject;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.platform.m.base.rest.BaseController;
import cn.geelato.web.platform.m.base.rest.RestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Created by hongxq on 2014/5/10.
 */
@Controller
@RequestMapping(value = "/api/sys/auth")
public class AuthRestController extends BaseController {

    @Autowired
    private AccountService accountService;


    private final Logger logger = LoggerFactory.getLogger(AuthRestController.class);

    @RequestMapping(value = "/login", method = RequestMethod.POST)
    @ResponseBody
    public Map login(@RequestBody User user, HttpServletRequest req) {
        Subject currentUser = SecurityUtils.getSubject();
        if (!currentUser.isAuthenticated()) {
            //collect user principals and credentials in a gui specific manner
            //such as username/password html form, X509 certificate, OpenID, etc.
            //We'll use the username/password example here since it is the most common.
            //(do you know what movie this is from? ;)
            UsernamePasswordToken token = new UsernamePasswordToken(user.getLoginName(), user.getPassword());
            //this is all you have to do to support 'remember me' (no config - built in!):
            boolean rememberMe = Boolean.parseBoolean(req.getParameter("remember"));
            token.setRememberMe(rememberMe);
            try {
                if (logger.isDebugEnabled()) {
                    logger.debug("User [" + token.getUsername() + "] logging in ... ");
                }
                currentUser.login(token);
                //if no exception, that's it, we're done!
                if (logger.isDebugEnabled()) {
                    logger.debug("User [" + currentUser.getPrincipal() + "] login successfully.");
                }
            } catch (UnknownAccountException uae) {
                //username wasn't in the system, show them an error message?
                throw new RestException(HttpStatus.UNAUTHORIZED, "无效的用户名！");
            } catch (IncorrectCredentialsException ice) {
                //password didn't match, try again?
                throw new RestException(HttpStatus.UNAUTHORIZED, "无效的密码！");
            } catch (LockedAccountException lae) {
                //account for that username is locked - can't login.  Show them a message?
                throw new RestException(HttpStatus.FORBIDDEN, "用户账号已被锁！");
            } catch (AuthenticationException ae) {
                //unexpected condition - error?
                throw new RestException(HttpStatus.BAD_REQUEST, "登录失败！[" + ae.getMessage() + "]");
            }
        }
        try {
            user = dao.queryForObject(User.class, "loginName", user.getLoginName());
            user.setSalt("");
            user.setPassword("");
            user.setPlainPassword("");
        } catch (EmptyResultDataAccessException e) {
            throw new RestException(HttpStatus.UNAUTHORIZED, "无效的用户名！");
        }
        return accountService.wrapUser(user);
    }

//    @RequestMapping(value = "/loginSecurity", method = RequestMethod.POST)
//    @ResponseBody
//    public ApiResult loginMD5(@RequestBody User user, HttpServletRequest req) {
//        ApiResult apiResult = new ApiResult();
//        apiResult.setData(login(user, req));
//        return apiResult;
//    }


    @RequestMapping(value = "/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        if (!SecurityHelper.isAuthenticatedForCurrentUser()) {
            logger.debug("No User to logout.");
        } else {
            String name = SecurityHelper.getCurrentUser().getName();
            Subject currentUser = SecurityUtils.getSubject();
            currentUser.logout();
            logger.debug("User [" + name + "] logout successfully.");
        }
    }


    /**
     * 获取当前用户的菜单 TODO 待按当前用户过滤
     *
     */
//    @RequestMapping(value = "/currentUserMenu", method = {RequestMethod.POST, RequestMethod.GET})
//    @ResponseBody
//    public ApiResult getCurrentUserMenu(HttpServletRequest req) {
//        // 菜单
//        Map map = new HashMap<>();
//        map.put("userId", 1);
//        List<Map<String, Object>> menuItemList = dao.queryForMapList("select_platform_menu", map);
//
//        return new ApiResult().setData(menuItemList);
//    }

    /**
     * 用于管理员重置密码
     *
     * @param userId         用户id
     * @param passwordLength 默认为8位，最长为32位
     */
    @RequestMapping(value = "/resetPassword", method = {RequestMethod.POST, RequestMethod.GET})
    @ResponseBody
    public ApiResult resetPassword(@RequestParam Long userId, @RequestParam(defaultValue = "8", required = false) int passwordLength) {
        User user = dao.queryForObject(User.class, userId);
        String plainPassword = RandomStringUtils.randomAlphanumeric(passwordLength > 32 ? 32 : passwordLength);
        user.setPlainPassword(plainPassword);
        accountService.entryptPassword(user);
        dao.save(user);
        return new ApiResult().setData(plainPassword);
    }

}
