package cn.geelato.web.platform.srv.weixin.miniapp;

import cn.geelato.lang.exception.CoreException;

/**
 * 微信小程序绑定冲突 409 异常（严格一对一约束）：
 * 该微信已绑定其他账号(20013) / 该账号已绑定其他微信(20014)，见 {@link MiniAppErrorCodes}。
 * <p>
 * 码值与提示语由构造传入；常规业务事件，不记服务端日志、不落异常表。
 */
public class MiniAppBindConflictException extends CoreException {

    public MiniAppBindConflictException(int errorCode, String message) {
        super(errorCode, message);
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
