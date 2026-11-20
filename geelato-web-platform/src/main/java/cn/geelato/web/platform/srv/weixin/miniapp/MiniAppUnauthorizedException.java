package cn.geelato.web.platform.srv.weixin.miniapp;

import cn.geelato.lang.exception.CoreException;

/**
 * 微信小程序登录/绑定 401 族异常（码值 20006-20011，见 {@link MiniAppErrorCodes}）：
 * 凭证无效(20006)/凭证已使用(20007)/手机号授权失败(20008)/票据失效(20009)/
 * 微信身份缺失(20010)/未绑定账号(20011)。
 * <p>
 * 码值与提示语由构造传入；常规业务事件，不记服务端日志、不落异常表。
 */
public class MiniAppUnauthorizedException extends CoreException {

    public MiniAppUnauthorizedException(int errorCode, String message) {
        super(errorCode, message);
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
