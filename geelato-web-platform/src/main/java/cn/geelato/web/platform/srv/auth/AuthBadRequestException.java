package cn.geelato.web.platform.srv.auth;

import cn.geelato.lang.exception.CoreException;
import cn.geelato.web.platform.exception.PlatformErrorCodes;

public class AuthBadRequestException extends CoreException {

    public AuthBadRequestException() {
        super(PlatformErrorCodes.AUTH_BAD_REQUEST);
    }

    public AuthBadRequestException(String message) {
        super(PlatformErrorCodes.AUTH_BAD_REQUEST, message);
    }

    public AuthBadRequestException(String message, Throwable cause) {
        super(PlatformErrorCodes.AUTH_BAD_REQUEST, message, cause);
    }
}
