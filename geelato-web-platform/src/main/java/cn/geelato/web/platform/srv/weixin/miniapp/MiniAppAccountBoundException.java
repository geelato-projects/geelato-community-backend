package cn.geelato.web.platform.srv.weixin.miniapp;

import cn.geelato.lang.exception.CoreException;

/**
 * 目标账号已绑定了其他微信（严格一对一约束：一个账号只能被一个微信号绑定）。
 */
public class MiniAppAccountBoundException extends CoreException {

    public static final int ERROR_CODE = 20014;

    public MiniAppAccountBoundException() {
        this("该账号已绑定其他微信，请先解绑");
    }

    public MiniAppAccountBoundException(String message) {
        super(ERROR_CODE, message);
    }

    @Override
    public int getHttpStatus() {
        return 409;
    }

    @Override
    public boolean shouldLog() {
        return false;
    }
}
