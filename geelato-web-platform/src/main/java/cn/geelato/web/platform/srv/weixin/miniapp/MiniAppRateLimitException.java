package cn.geelato.web.platform.srv.weixin.miniapp;

import cn.geelato.lang.exception.CoreException;

/**
 * 操作频率超限（login/pre-bind/bind 的防撞库与票据暴力尝试限流）。
 */
public class MiniAppRateLimitException extends CoreException {

    public static final int ERROR_CODE = 20015;

    public MiniAppRateLimitException() {
        this("操作过于频繁，请稍后再试");
    }

    public MiniAppRateLimitException(String message) {
        super(ERROR_CODE, message);
    }

    @Override
    public int getHttpStatus() {
        return 429;
    }

    @Override
    public boolean shouldLog() {
        return false;
    }
}
