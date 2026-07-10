package cn.geelato.web.platform.srv.auth;

import cn.geelato.lang.exception.CoreException;

public class AccountOperationForbiddenException extends CoreException {
    private static final int DEFAULT_CODE = 403;
    private static final String DEFAULT_MSG = "无权操作该用户";

    public AccountOperationForbiddenException() {
        super(DEFAULT_CODE, DEFAULT_MSG);
    }

    public AccountOperationForbiddenException(String message) {
        super(DEFAULT_CODE, message);
    }

    public AccountOperationForbiddenException(String message, Throwable cause) {
        super(DEFAULT_CODE, message, cause);
    }
}
