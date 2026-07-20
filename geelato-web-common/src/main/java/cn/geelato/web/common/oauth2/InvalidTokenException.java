package cn.geelato.web.common.oauth2;

import cn.geelato.lang.exception.CoreException;
import cn.geelato.web.common.exception.WebCommonErrorCodes;

public class InvalidTokenException extends CoreException {

    public InvalidTokenException() {
        super(WebCommonErrorCodes.INVALID_TOKEN);
    }

    public InvalidTokenException(String message) {
        super(WebCommonErrorCodes.INVALID_TOKEN, message);
    }

    public InvalidTokenException(String message, Throwable throwable) {
        super(WebCommonErrorCodes.INVALID_TOKEN, message, throwable);
    }
}
