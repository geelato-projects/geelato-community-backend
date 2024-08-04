package cn.geelato.web.platform.m.security.rest;

import cn.geelato.web.platform.m.security.entity.User;
import cn.geelato.web.platform.m.security.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.platform.m.base.rest.BaseController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;


@Controller
@RequestMapping(value = "/api/sys/account")
public class AccountRestController extends BaseController {

    @Autowired
    protected AccountService accountService;

    private Logger logger = LoggerFactory.getLogger(AccountRestController.class);

    @RequestMapping(method = RequestMethod.POST)
    @ResponseBody
    public ApiResult create(@RequestBody User user, HttpServletRequest req) {
        accountService.registerUser(user);
        return new ApiResult();
    }
}
