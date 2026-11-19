package cn.geelato.web.platform.srv.weixin.miniapp;

import cn.geelato.lang.exception.CoreException;

/**
 * 微信小程序登录凭证（js_code）无效（过期、格式错误、appid 不匹配等）。
 */
public class MiniAppCredentialInvalidException extends CoreException {

    public static final int ERROR_CODE = 20006;

    public MiniAppCredentialInvalidException() {
        this("登录凭证无效，请重新进入小程序");
    }

    public MiniAppCredentialInvalidException(String message) {
        super(ERROR_CODE, message);
    }

    @Override
    public int getHttpStatus() {
        return 401;
    }

    @Override
    public boolean shouldLog() {
        return false;
    }
}
