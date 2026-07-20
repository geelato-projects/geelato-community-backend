package cn.geelato.web.common.interceptor;

import cn.geelato.lang.exception.CoreException;
import cn.geelato.web.common.exception.WebCommonErrorCodes;

/**
 * 统一的401未授权异常
 * 用于替代InvalidTokenException和OAuthGetUserFailException
 */
public class UnauthorizedException extends CoreException {

    public UnauthorizedException() {
        super(WebCommonErrorCodes.UNAUTHORIZED);
    }

    public UnauthorizedException(String message) {
        super(WebCommonErrorCodes.UNAUTHORIZED, message);
    }

    public UnauthorizedException(String message, Throwable throwable) {
        super(WebCommonErrorCodes.UNAUTHORIZED, message, throwable);
    }
}
