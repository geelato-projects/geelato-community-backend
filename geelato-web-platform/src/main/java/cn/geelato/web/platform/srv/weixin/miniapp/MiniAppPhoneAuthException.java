package cn.geelato.web.platform.srv.weixin.miniapp;

import cn.geelato.lang.exception.CoreException;

/**
 * 微信手机号授权失败（getPhoneNumber 凭证无效/过期/已消费等）。
 */
public class MiniAppPhoneAuthException extends CoreException {

    public static final int ERROR_CODE = 20008;

    public MiniAppPhoneAuthException() {
        this("手机号授权失败，请重新授权");
    }

    public MiniAppPhoneAuthException(String message) {
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
