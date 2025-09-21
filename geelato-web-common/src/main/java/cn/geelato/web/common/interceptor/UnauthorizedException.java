package cn.geelato.web.common.interceptor;

import cn.geelato.lang.exception.CoreException;

/**
 * 统一的401未授权异常
 * 用于替代InvalidTokenException和OAuthGetUserFailException
 */
public class UnauthorizedException extends CoreException {
    private static final int DEFAULT_CODE = 401;
    private static final String DEFAULT_MSG = "未授权访问[OAUTH]";

    public UnauthorizedException() {
        super(DEFAULT_CODE, DEFAULT_MSG);
    }

    public UnauthorizedException(String message) {
        super(DEFAULT_CODE, message);
    }

    public UnauthorizedException(String message, Throwable throwable) {
        super(DEFAULT_CODE, message, throwable);
    }
}