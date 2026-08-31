package cn.geelato.web.common.interceptor;

import cn.geelato.lang.exception.CoreException;

/**
 * 统一的401未授权异常
 * 用于替代InvalidTokenException和OAuthGetUserFailException
 */
public class UnauthorizedException extends CoreException {

    public static final int ERROR_CODE = 20005;

    public UnauthorizedException() {
        this("未授权访问，请重新登录");
    }

    public UnauthorizedException(String message) {
        super(ERROR_CODE, message);
    }

    @Override
    public int getHttpStatus() {
        return 401;
    }
}
