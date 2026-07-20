package cn.geelato.web.platform.utils;

import cn.geelato.lang.exception.CoreException;
import cn.geelato.web.platform.exception.PlatformErrorCodes;

public class GqlResolveException extends CoreException {

    public GqlResolveException() {
        super(PlatformErrorCodes.GQL_RESOLVE);
    }

    public GqlResolveException(int code, String msg) {
        super(code, msg);
    }

    public GqlResolveException(int code, String msg, Throwable cause) {
        super(code, msg, cause);
    }
}
