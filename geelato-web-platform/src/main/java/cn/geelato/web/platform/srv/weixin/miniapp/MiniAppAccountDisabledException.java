package cn.geelato.web.platform.srv.weixin.miniapp;

import cn.geelato.lang.exception.CoreException;

/**
 * 目标账号已停用（platform_user.enable_status = 0）。
 */
public class MiniAppAccountDisabledException extends CoreException {

    public static final int ERROR_CODE = 20012;

    public MiniAppAccountDisabledException() {
        this("账号已停用，请联系业务员");
    }

    public MiniAppAccountDisabledException(String message) {
        super(ERROR_CODE, message);
    }

    @Override
    public int getHttpStatus() {
        return 403;
    }

    @Override
    public boolean shouldLog() {
        return false;
    }
}
