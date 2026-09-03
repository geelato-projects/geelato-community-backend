package cn.geelato.web.platform.srv.auth;

import cn.geelato.lang.exception.CoreException;

public class AccountOperationForbiddenException extends CoreException {

    public static final int ERROR_CODE = 20004;

    public AccountOperationForbiddenException() {
        super(ERROR_CODE, "无权操作该用户");
    }

    @Override
    public int getHttpStatus() {
        return 403;
    }

    /** 无权操作属用户权限范畴（前端提示即可），非服务端错误，不记录服务端日志、不落异常表。 */
    @Override
    public boolean shouldLog() {
        return false;
    }
}
