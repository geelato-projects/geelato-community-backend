package cn.geelato.web.platform.srv.auth;

import cn.geelato.lang.exception.CoreException;

public class AuthBadRequestException extends CoreException {

    public static final int ERROR_CODE = 20003;

    public AuthBadRequestException(String message) {
        super(ERROR_CODE, message);
    }

    public AuthBadRequestException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }

    @Override
    public int getHttpStatus() {
        return 400;
    }

    /** 鉴权链路的参数错误（如验证码错误），用户输入问题非服务端错误，不记录服务端日志、不落异常表。 */
    @Override
    public boolean shouldLog() {
        return false;
    }
}
