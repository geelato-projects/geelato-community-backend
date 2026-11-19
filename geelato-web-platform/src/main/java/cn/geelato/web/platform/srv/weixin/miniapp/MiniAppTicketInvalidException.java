package cn.geelato.web.platform.srv.weixin.miniapp;

import cn.geelato.lang.exception.CoreException;

/**
 * 绑定确认票据（preBindTicket）无效：签名不匹配、已过期、nonce 已消费或绑定目标账号已失效。
 */
public class MiniAppTicketInvalidException extends CoreException {

    public static final int ERROR_CODE = 20009;

    public MiniAppTicketInvalidException() {
        this("绑定确认已过期，请重新发起绑定");
    }

    public MiniAppTicketInvalidException(String message) {
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
