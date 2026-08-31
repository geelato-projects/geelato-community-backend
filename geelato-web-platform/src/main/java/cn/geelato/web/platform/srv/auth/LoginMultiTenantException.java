package cn.geelato.web.platform.srv.auth;

import cn.geelato.lang.exception.CoreException;

public class LoginMultiTenantException extends CoreException {

    /** 公开错误码常量，保留以兼容 {@code JWTAuthController} 等外部引用（历史码值 20001，保持不变）。 */
    public static final int DEFAULT_CODE = 20001;

    public LoginMultiTenantException() {
        super(DEFAULT_CODE, "请选择租户");
    }
}
