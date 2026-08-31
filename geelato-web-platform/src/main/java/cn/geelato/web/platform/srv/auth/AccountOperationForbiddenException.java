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
}
