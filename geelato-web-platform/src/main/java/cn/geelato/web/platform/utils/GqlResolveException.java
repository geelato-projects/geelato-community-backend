package cn.geelato.web.platform.utils;

import cn.geelato.lang.exception.CoreException;

public class GqlResolveException extends CoreException {
    private static final int DEFAULT_CODE = 2000;
    private static final String DEFAULT_MSG = "Gql Resolve Exception";
    public GqlResolveException(){
        super(DEFAULT_CODE,DEFAULT_MSG);
    }
    public GqlResolveException(int code, String msg) {
        super(code, msg);
    }
    public GqlResolveException(int code, String msg, Throwable cause) {
        super(code, msg, cause);
    }
}
