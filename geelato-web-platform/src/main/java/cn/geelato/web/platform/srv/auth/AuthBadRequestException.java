package cn.geelato.web.platform.srv.auth;

import cn.geelato.lang.exception.CoreException;

public class AuthBadRequestException extends CoreException {
    private static final int DEFAULT_CODE = 400;
    private static final String DEFAULT_MSG = "请求参数错误";

    public AuthBadRequestException() {
        super(DEFAULT_CODE, DEFAULT_MSG);
    }

    public AuthBadRequestException(String message) {
        super(DEFAULT_CODE, message);
    }

    public AuthBadRequestException(String message, Throwable cause) {
        super(DEFAULT_CODE, message, cause);
    }
}
