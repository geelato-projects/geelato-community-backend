package cn.geelato.web.platform.interceptor;

import cn.geelato.lang.exception.CoreException;

public class OAuthGetUserFailException extends CoreException {
    private static final int DEFAULT_CODE = 4000;
    private static final String DEFAULT_MSG = "OAuth GetUserFailException";

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