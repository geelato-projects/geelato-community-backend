package cn.geelato.web.platform.srv.security;


import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.utils.HttpUtils;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.boot.properties.WeixinConfigurationProperties;
import cn.geelato.meta.User;
import cn.geelato.web.platform.srv.security.service.UserService;
import com.alibaba.fastjson2.JSON;
import jakarta.validation.constraints.Null;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApiRestController(value = "/security/wechat")
@Slf4j
public class WeChatController extends BaseController {
    private static final Class<User> CLAZZ = User.class;
    private final WeixinConfigurationProperties wxConfiguration;
    private final UserService userService;

    @Autowired
    public WeChatController(WeixinConfigurationProperties wxConfiguration, UserService userService) {
        this.wxConfiguration = wxConfiguration;
        this.userService = userService;
    }

    /**
     * 取消绑定微信
     *
     * @param id
     */
    @RequestMapping(value = "/signOut/{id}", method = RequestMethod.POST)
    public ApiResult<Null> signOut(@PathVariable String id) {
        User user = userService.getModel(CLAZZ, id);
        Assert.notNull(user, ApiErrorMsg.IS_NULL);
        user.setWeixinUnionId(null);
        userService.updateModel(user);
        return ApiResult.successNoResult();
    }


    @RequestMapping(value = "/signIn/{id}", method = RequestMethod.POST)
    public ApiResult<?> bindWeChat(@PathVariable String id, String code) {
        // 获取用户信息
        User user = userService.getModel(CLAZZ, id);
        Assert.notNull(user, ApiErrorMsg.IS_NULL);
        // 获取微信access_token
        WeChatAccess access = getToken(wxConfiguration.getUrl(), wxConfiguration.getAppId(), wxConfiguration.getSecret(), code);
        if (access == null || Strings.isBlank(access.getUnionid())) {
            return ApiResult.fail("Failed to obtain the unionId of weChat");
        }
        // 判断是否已经绑定过微信
        Map<String, Object> params = new HashMap<>();
        params.put("weixinUnionId", access.getUnionid());
        List<User> userList = userService.queryModel(CLAZZ, params);
        if (userList != null && !userList.isEmpty()) {
            return ApiResult.fail("The unionId of weChat has been occupied");
        }
        // 绑定微信
        user.setWeixinUnionId(access.getUnionid());
        userService.updateModel(user);
        return ApiResult.successNoResult();
    }

    private static WeChatAccess getToken(String host, String appId, String secret, String code) {
        String ACCESS_TOKEN = "%s/sns/oauth2/access_token?appid=%s&secret=%s&code=%s&grant_type=authorization_code";
        try {
            String wxRst = HttpUtils.doGet(String.format(ACCESS_TOKEN, host, appId, secret, code), null);
            if (StringUtils.isNotEmpty(wxRst)) {
                if (wxRst.contains("unionid")) {
                    return JSON.parseObject(wxRst, WeChatAccess.class);
                } else if (wxRst.contains("errcode")) {
                    WeChatError wxError = JSON.parseObject(wxRst, WeChatError.class);
                    throw new RuntimeException(wxError.getErrcode() + ": " + wxError.getErrmsg());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    @Getter
    @Setter
    public static class WeChatAccess {
        // 接口调用凭证
        private String access_token;
        // access_token接口调用凭证超时时间，单位（秒）
        private Long expires_in;
        // 用户刷新access_token
        private String refresh_token;
        // 授权用户唯一标识
        private String openid;
        // 用户授权的作用域，使用逗号（,）分隔
        private String scope;
        // 当且仅当该网站应用已获得该用户的userinfo授权时，才会出现该字段。
        private String unionid;
    }

    @Getter
    @Setter
    public static class WeChatError {
        private Integer errcode;
        private String errmsg;
    }
}
