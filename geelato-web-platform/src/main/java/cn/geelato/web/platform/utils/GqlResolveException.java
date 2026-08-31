package cn.geelato.web.platform.utils;

import cn.geelato.lang.exception.CoreException;

public class GqlResolveException extends CoreException {

    public static final int ERROR_CODE = 10004;

    public GqlResolveException() {
        super(ERROR_CODE, "请求解析失败，请检查表达式");
    }
}
