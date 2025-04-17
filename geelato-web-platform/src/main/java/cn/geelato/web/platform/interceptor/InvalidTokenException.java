package cn.geelato.web.platform.interceptor;

import cn.geelato.lang.exception.CoreException;

public class InvalidTokenException extends CoreException {
    private static final int DEFAULT_CODE = 3000;
    private static final String DEFAULT_MSG = "InvalidTokenException";

    public InvalidTokenException() {
        super(DEFAULT_CODE,DEFAULT_MSG);
    }

    public InvalidTokenException(String message) {
        super(DEFAULT_CODE, message);
    }

    public InvalidTokenException(String message, Throwable throwable) {
        super(DEFAULT_CODE, message, throwable);
    }
}
