package cn.geelato.core.mql.parser;

import cn.geelato.lang.exception.CoreException;

/**
 * 分页查询参数非法（非整数、非正数或超出上限）。
 * <p>由 {@code ParameterOperator} 的统一分页解析抛出，HTTP 状态码 400，
 * 文案包含参数名与实际传入值（如"分页参数 pageSize=abc 不是有效整数"），便于调用方自助修正。</p>
 */
public class InvalidPageParamException extends CoreException {

    public static final int ERROR_CODE = 10005;

    public InvalidPageParamException(String msg) {
        super(ERROR_CODE, msg);
    }

    @Override
    public int getHttpStatus() {
        return 400;
    }
}
