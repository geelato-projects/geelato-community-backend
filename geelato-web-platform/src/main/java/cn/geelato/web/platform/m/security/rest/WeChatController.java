package cn.geelato.web.platform.m.security.rest;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.utils.wechat.WeChatAccess;
import cn.geelato.utils.wechat.WeChatUtils;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.security.WXConfiguration;
import cn.geelato.web.platform.m.security.entity.User;
import cn.geelato.web.platform.m.security.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@ApiRestController(value = "/security/wechat")
@Slf4j
public class WeChatController extends BaseController {
    private static final Class<User> CLAZZ = User.class;

    private final UserService userService;
    private final WXConfiguration wxConfiguration;

    @Autowired
    public WeChatController(UserService userService, WXConfiguration wxConfiguration) {
        this.userService = userService;
        this.wxConfiguration = wxConfiguration;
    }

    @RequestMapping(value = "/signOut/{id}", method = RequestMethod.POST)
    public ApiResult signOut(@PathVariable(required = true) String id) {
        User user = userService.getModel(CLAZZ, id);
        Assert.notNull(user, ApiErrorMsg.IS_NULL);
        user.setUnionId(null);
        userService.updateModel(user);
        return ApiResult.successNoResult();
    }

    @RequestMapping(value = "/signIn/{id}", method = RequestMethod.POST)
    public ApiResult bindWeChat(@PathVariable(required = true) String id, String code) {
        User user = userService.getModel(CLAZZ, id);
        Assert.notNull(user, ApiErrorMsg.IS_NULL);
        WeChatAccess access = WeChatUtils.accessToken(wxConfiguration.getUrl(), wxConfiguration.getAppId(), wxConfiguration.getSecret(), code);
        if (access == null || Strings.isBlank(access.getUnionid())) {
            return ApiResult.fail("Failed to obtain the unionId of weChat");
        }
        user.setUnionId(access.getUnionid());
        userService.updateModel(user);
        return ApiResult.successNoResult();
    }

    @RequestMapping(value = "/getId", method = RequestMethod.POST)
    public ApiResult getId(String code) {
        WeChatAccess access = WeChatUtils.accessToken(wxConfiguration.getUrl(), wxConfiguration.getAppId(), wxConfiguration.getSecret(), code);
        if (access == null || Strings.isBlank(access.getUnionid())) {
            return ApiResult.fail("Failed to obtain the unionId of weChat");
        }
        return ApiResult.success(access.getUnionid());
    }
}
