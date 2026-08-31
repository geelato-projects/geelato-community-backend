package cn.geelato.lang.exception;

/**
 * 当前版本不支持该操作时抛出。
 *
 * @author geelato
 */
public class UnSupportedVersionException extends CoreException {

    public static final int ERROR_CODE = 10006;

    public UnSupportedVersionException() {
        super(ERROR_CODE, "当前版本不支持该操作");
    }

    public UnSupportedVersionException(String msg) {
        super(ERROR_CODE, msg);
    }

    public UnSupportedVersionException(String msg, Throwable cause) {
        super(ERROR_CODE, msg, cause);
    }
}
