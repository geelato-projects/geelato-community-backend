package cn.geelato.utils.wechat;

import cn.geelato.utils.HttpUtils;
import cn.geelato.utils.StringUtils;
import com.alibaba.fastjson2.JSON;

import java.io.IOException;

public class WeChatUtils {
    public static final String ACCESS_TOKEN = "%s/sns/oauth2/access_token?appid=%s&secret=%s&code=%s&grant_type=authorization_code";
    public static final String USER_INFO = "%s/sns/userinfo?access_token=%s&openid=%s";

    public static WeChatAccess accessToken(String host, String appId, String secret, String code) {
        try {
            String wxRst = HttpUtils.doGet(String.format(ACCESS_TOKEN, host, appId, secret, code), null);
            if (StringUtils.isNotEmpty(wxRst)) {
                if (wxRst.indexOf("unionid") != -1) {
                    return JSON.parseObject(wxRst, WeChatAccess.class);
                } else if (wxRst.indexOf("errcode") != -1) {
                    WeChatError wxError = JSON.parseObject(wxRst, WeChatError.class);
                    throw new RuntimeException(wxError.getErrcode() + ": " + wxError.getErrmsg());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public static WeChatUserInfo userInfo(String token, String openId) {
        try {
            String wxRst = HttpUtils.doGet(String.format(USER_INFO, token, openId), null);
            if (!StringUtils.isEmpty(wxRst)) {
                if (wxRst.indexOf("unionid") != -1) {
                    return JSON.parseObject(wxRst, WeChatUserInfo.class);
                } else if (wxRst.indexOf("errcode") != -1) {
                    WeChatError wxError = JSON.parseObject(wxRst, WeChatError.class);
                    throw new RuntimeException(wxError.getErrcode() + ": " + wxError.getErrmsg());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }
}
