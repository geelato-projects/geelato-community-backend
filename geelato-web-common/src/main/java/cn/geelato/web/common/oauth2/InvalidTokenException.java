package cn.geelato.web.common.oauth2;

import cn.geelato.lang.exception.CoreException;

public class InvalidTokenException extends CoreException {
    private static final int DEFAULT_CODE = 10007;
    private static final String DEFAULT_MSG = "令牌校验异常[InvalidToken]";

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
