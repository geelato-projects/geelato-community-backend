package cn.geelato.web.platform.m.security.rest;


import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.web.platform.annotation.ApiRestController;
import cn.geelato.web.platform.m.BaseController;
import cn.geelato.web.platform.m.security.WxChatConfiguration;
import cn.geelato.web.platform.m.security.entity.User;
import cn.geelato.web.platform.m.security.service.UserService;
import cn.geelato.web.platform.m.security.wechat.WeChatAccess;
import cn.geelato.web.platform.m.security.wechat.WeChatUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApiRestController(value = "/security/wechat")
@Slf4j
public class WeChatController extends BaseController {
    private static final Class<User> CLAZZ = User.class;
    private final WxChatConfiguration wxConfiguration;
    private final UserService userService;

    @Autowired
    public WeChatController(WxChatConfiguration wxConfiguration, UserService userService) {
        this.wxConfiguration = wxConfiguration;
        this.userService = userService;
    }

    /**
     * 取消绑定微信
     *
     * @param id
     * @return
     */
    @RequestMapping(value = "/signOut/{id}", method = RequestMethod.POST)
    public ApiResult signOut(@PathVariable(required = true) String id) {
        User user = userService.getModel(CLAZZ, id);
        Assert.notNull(user, ApiErrorMsg.IS_NULL);
        user.setUnionId(null);
        userService.updateModel(user);
        return ApiResult.successNoResult();
    }

    /**
     * 绑定微信
     *
     * @param id
     * @param code
     * @return
     */
    @RequestMapping(value = "/signIn/{id}", method = RequestMethod.POST)
    public ApiResult bindWeChat(@PathVariable(required = true) String id, String code) {
        // 获取用户信息
        User user = userService.getModel(CLAZZ, id);
        Assert.notNull(user, ApiErrorMsg.IS_NULL);
        // 获取微信access_token
        WeChatAccess access = WeChatUtil.accessToken(wxConfiguration.getUrl(), wxConfiguration.getAppId(), wxConfiguration.getSecret(), code);
        if (access == null || Strings.isBlank(access.getUnionid())) {
            return ApiResult.fail("Failed to obtain the unionId of weChat");
        }
        // 判断是否已经绑定过微信
        Map<String, Object> params = new HashMap<>();
        params.put("unionId", access.getUnionid());
        List<User> userList = userService.queryModel(CLAZZ, params);
        if (userList != null && userList.size() > 0) {
            return ApiResult.fail("The unionId of weChat has been occupied");
        }
        // 绑定微信
        user.setUnionId(access.getUnionid());
        userService.updateModel(user);
        return ApiResult.successNoResult();
    }
}
