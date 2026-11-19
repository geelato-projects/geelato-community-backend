package cn.geelato.web.platform.srv.weixin.miniapp;

import cn.geelato.lang.exception.CoreException;

/**
 * 微信登录凭证已被使用（js_code 一次性，重复消费，微信 errcode 40163）。
 */
public class MiniAppCredentialUsedException extends CoreException {

    public static final int ERROR_CODE = 20007;

    public MiniAppCredentialUsedException() {
        this("登录凭证已被使用，请重新获取");
    }

    public MiniAppCredentialUsedException(String message) {
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
