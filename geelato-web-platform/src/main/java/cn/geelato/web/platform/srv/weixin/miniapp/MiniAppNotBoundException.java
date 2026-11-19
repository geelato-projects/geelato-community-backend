package cn.geelato.web.platform.srv.weixin.miniapp;

import cn.geelato.lang.exception.CoreException;

/**
 * 微信身份未绑定平台账号（login）或当前微信未绑定账号（unbind）。
 */
public class MiniAppNotBoundException extends CoreException {

    public static final int ERROR_CODE = 20011;

    public MiniAppNotBoundException() {
        this("请先绑定工作台账号");
    }

    public MiniAppNotBoundException(String message) {
        super(ERROR_CODE, message);
    }

    @Override
    public int getHttpStatus() {
        return 401;
    }

    @Override
    public boolean shouldLog() {
        return false;
    }
}
