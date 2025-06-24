package cn.geelato.lang.exception;

public class UnSupportedVersionException extends CoreException{
    private static final int DEFAULT_CODE = 10006;
    private static final String DEFAULT_MSG = "The current version does not support this operation！";

    public UnSupportedVersionException(){
        super(DEFAULT_CODE, DEFAULT_MSG);
    }
    public UnSupportedVersionException(String msg) {
        super(DEFAULT_CODE, msg);
    }
    public UnSupportedVersionException(String msg, Throwable cause) {
        super(DEFAULT_CODE, msg, cause);
    }
}
