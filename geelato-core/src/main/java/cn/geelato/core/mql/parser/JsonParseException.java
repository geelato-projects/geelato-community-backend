package cn.geelato.core.mql.parser;

import cn.geelato.core.orm.CoreErrorCodes;
import cn.geelato.lang.exception.CoreException;

public class JsonParseException extends CoreException {

    public JsonParseException() {
        super(CoreErrorCodes.MQL_JSON_PARSE);
    }

    public JsonParseException(String msg) {
        super(CoreErrorCodes.MQL_JSON_PARSE, msg);
    }

    public JsonParseException(String msg, Throwable cause) {
        super(CoreErrorCodes.MQL_JSON_PARSE, msg, cause);
    }
}
