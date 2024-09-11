package cn.geelato.web.platform.m.security.rest;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.base.rest.BaseController;
import cn.geelato.web.platform.m.security.entity.User;
import cn.geelato.web.platform.m.security.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;


@ApiRestController("/sys/account")
public class AccountRestController extends BaseController {

    protected AccountService accountService;

    @Autowired
    public AccountRestController(AccountService accountService) {
        this.accountService = accountService;
    }

    @RequestMapping(method = RequestMethod.POST)
    public ApiResult<NullResult> create(@RequestBody User user, HttpServletRequest req) {
        accountService.registerUser(user);
        return ApiResult.successNoResult();
    }
}
