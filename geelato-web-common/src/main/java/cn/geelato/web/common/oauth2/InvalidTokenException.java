package cn.geelato.web.common.oauth2;

import cn.geelato.lang.exception.CoreException;

public class InvalidTokenException extends CoreException {

    public static final int ERROR_CODE = 20002;

    public InvalidTokenException() {
        this("令牌校验异常，请重新登录");
    }

    public InvalidTokenException(String message) {
        super(ERROR_CODE, message);
    }

    /** 常规业务事件（令牌过期/校验失败，前端自动跳转登录），不记录服务端日志、不落异常表。 */
    @Override
    public boolean shouldLog() {
        return false;
    }
}
