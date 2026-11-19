package cn.geelato.web.platform.srv.weixin.miniapp;

import cn.geelato.lang.exception.CoreException;

/**
 * 无法获取微信身份标识（jscode2session 成功但未返回 unionid，如小程序未绑定微信开放平台）。
 */
public class MiniAppIdentityUnavailableException extends CoreException {

    public static final int ERROR_CODE = 20010;

    public MiniAppIdentityUnavailableException() {
        this("无法获取微信身份标识，请联系管理员");
    }

    public MiniAppIdentityUnavailableException(String message) {
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
