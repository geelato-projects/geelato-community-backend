package cn.geelato.core.mql.parser;

import cn.geelato.lang.exception.CoreException;

public class JsonParseException extends CoreException {
    private static final int DEFAULT_CODE = 10008;
    private static final String DEFAULT_MSG = "MQL json解析异常";

    public JsonParseException() {
        super(DEFAULT_CODE, DEFAULT_MSG);
    }

    public JsonParseException(String msg) {
        super(DEFAULT_CODE, msg);
    }

    public JsonParseException(String msg, Throwable cause) {
        super(DEFAULT_CODE, msg, cause);
    }
}
