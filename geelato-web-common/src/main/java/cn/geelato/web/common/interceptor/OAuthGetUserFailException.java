package cn.geelato.web.common.interceptor;

import cn.geelato.lang.exception.CoreException;

public class OAuthGetUserFailException extends CoreException {
    private static final int DEFAULT_CODE = 10004;
    private static final String DEFAULT_MSG = "获取用户失败[OAuth]";

    public OAuthGetUserFailException() {
        super(DEFAULT_CODE, DEFAULT_MSG);
    }

    public OAuthGetUserFailException(String message) {
        super(DEFAULT_CODE, message);
    }

    public OAuthGetUserFailException(String message, Throwable throwable) {
        super(DEFAULT_CODE, message, throwable);
    }
}