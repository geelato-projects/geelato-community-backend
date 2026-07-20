package cn.geelato.lang.exception;

import cn.geelato.lang.exception.LangErrorCodes;

/**
 * 当前版本不支持该操作时抛出。
 *
 * @author geelato
 */
public class UnSupportedVersionException extends CoreException {

    public UnSupportedVersionException() {
        super(LangErrorCodes.UNSUPPORTED_VERSION);
    }

    public UnSupportedVersionException(String msg) {
        super(LangErrorCodes.UNSUPPORTED_VERSION, msg);
    }

    public UnSupportedVersionException(String msg, Throwable cause) {
        super(LangErrorCodes.UNSUPPORTED_VERSION, msg, cause);
    }
}
