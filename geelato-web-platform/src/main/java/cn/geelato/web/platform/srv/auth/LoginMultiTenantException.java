package cn.geelato.web.platform.srv.auth;

import cn.geelato.lang.exception.CoreException;
import cn.geelato.web.platform.exception.PlatformErrorCodes;

public class LoginMultiTenantException extends CoreException {
    /** 公开错误码常量，保留以兼容 {@code JWTAuthController} 等外部引用。 */
    public static final int DEFAULT_CODE = PlatformErrorCodes.LOGIN_MULTI_TENANT.getCode();
    private static final String DEFAULT_MSG = "登录异常[MultiTenant]";

    public LoginMultiTenantException() {
        super(PlatformErrorCodes.LOGIN_MULTI_TENANT);
    }

    public LoginMultiTenantException(int code, String msg) {
        super(code, msg);
    }

    public LoginMultiTenantException(int code, String msg, Throwable cause) {
        super(code, msg, cause);
    }
}
