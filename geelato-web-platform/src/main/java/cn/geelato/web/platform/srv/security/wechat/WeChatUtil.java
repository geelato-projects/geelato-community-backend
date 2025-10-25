package cn.geelato.web.platform.srv.security.wechat;

import cn.geelato.utils.HttpUtils;
import cn.geelato.utils.StringUtils;
import com.alibaba.fastjson2.JSON;

import java.io.IOException;

public class WeChatUtil {
    private static final String ACCESS_TOKEN = "%s/sns/oauth2/access_token?appid=%s&secret=%s&code=%s&grant_type=authorization_code";

    public static WeChatAccess accessToken(String host, String appId, String secret, String code) {
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
}
