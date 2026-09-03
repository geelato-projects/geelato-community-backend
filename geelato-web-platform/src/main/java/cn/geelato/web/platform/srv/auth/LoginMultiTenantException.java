package cn.geelato.web.platform.srv.auth;

import cn.geelato.lang.exception.CoreException;

public class LoginMultiTenantException extends CoreException {

    /** 公开错误码常量，保留以兼容 {@code JWTAuthController} 等外部引用（历史码值 20001，保持不变）。 */
    public static final int DEFAULT_CODE = 20001;

    public LoginMultiTenantException() {
        super(DEFAULT_CODE, "请选择租户");
    }

    /** 常规登录流程事件（前端凭 20001 弹出租户选择框），非服务端错误，不记录服务端日志、不落异常表。 */
    @Override
    public boolean shouldLog() {
        return false;
    }
}
