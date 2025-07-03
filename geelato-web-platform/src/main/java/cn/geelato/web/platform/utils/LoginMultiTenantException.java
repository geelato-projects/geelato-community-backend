package cn.geelato.web.platform.utils;

import cn.geelato.lang.exception.CoreException;

public class LoginMultiTenantException extends CoreException {
    public static final int DEFAULT_CODE = 20001;
    private static final String DEFAULT_MSG = "登录异常[MultiTenant]";

    public LoginMultiTenantException() {
        super(DEFAULT_CODE, DEFAULT_MSG);
    }

    public LoginMultiTenantException(int code, String msg) {
        super(code, msg);
    }

    public LoginMultiTenantException(int code, String msg, Throwable cause) {
        super(code, msg, cause);
    }
}
