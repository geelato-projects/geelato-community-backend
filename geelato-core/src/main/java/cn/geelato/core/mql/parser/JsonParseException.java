package cn.geelato.core.mql.parser;

import cn.geelato.lang.exception.CoreException;

public class JsonParseException extends CoreException {

    public static final int ERROR_CODE = 10001;

    public JsonParseException() {
        super(ERROR_CODE, "请求解析失败，请检查数据格式");
    }
}
