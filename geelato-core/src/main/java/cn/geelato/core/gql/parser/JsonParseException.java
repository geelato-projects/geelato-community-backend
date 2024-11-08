package cn.geelato.core.gql.parser;

import cn.geelato.lang.exception.CoreException;

public class JsonParseException extends CoreException {
    private static final int DEFAULT_CODE = 8888;
    private static final String DEFAULT_MSG = "JsonParseException";

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
