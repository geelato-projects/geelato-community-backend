package cn.geelato.web.platform.srv.auth;

import cn.geelato.lang.exception.CoreException;
import cn.geelato.web.platform.exception.PlatformErrorCodes;

public class AccountOperationForbiddenException extends CoreException {

    public AccountOperationForbiddenException() {
        super(PlatformErrorCodes.ACCOUNT_OPERATION_FORBIDDEN);
    }

    public AccountOperationForbiddenException(String message) {
        super(PlatformErrorCodes.ACCOUNT_OPERATION_FORBIDDEN, message);
    }

    public AccountOperationForbiddenException(String message, Throwable cause) {
        super(PlatformErrorCodes.ACCOUNT_OPERATION_FORBIDDEN, message, cause);
    }
}
