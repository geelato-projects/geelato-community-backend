package cn.geelato.web.platform.srv.weixin.miniapp;

import cn.geelato.lang.exception.CoreException;

/**
 * 该微信 unionid 已绑定在其他账号上（严格一对一约束：一个微信号只能绑定一个账号）。
 */
public class MiniAppUnionIdOccupiedException extends CoreException {

    public static final int ERROR_CODE = 20013;

    public MiniAppUnionIdOccupiedException() {
        this("该微信已绑定其他账号，请先解绑");
    }

    public MiniAppUnionIdOccupiedException(String message) {
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
