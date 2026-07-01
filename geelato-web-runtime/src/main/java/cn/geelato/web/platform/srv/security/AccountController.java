package cn.geelato.web.platform.srv.security;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.api.NullResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.meta.User;
import cn.geelato.web.platform.srv.security.service.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;


@ApiRestController("/sys/account")
public class AccountController extends BaseController {

    protected AccountService accountService;

    @Autowired
    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @RequestMapping(method = RequestMethod.POST)
    public ApiResult<NullResult> create(@RequestBody User user) {
        accountService.registerUser(user);
        return ApiResult.successNoResult();
    }
}
