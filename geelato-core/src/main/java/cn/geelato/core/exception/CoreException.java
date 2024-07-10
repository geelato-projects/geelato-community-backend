package cn.geelato.core.exception;


public abstract class CoreException extends RuntimeException {
    private final String errorMsg;
    private final int errorCode;

    public CoreException(int code, String msg) {
        super(msg);
        this.errorMsg = msg;
        this.errorCode=code;
    }
    public CoreException(int code, String msg,Throwable cause) {
        super(msg,cause);
        this.errorMsg = msg;
        this.errorCode=code;
    }


    public int getErrorCode() {
        return errorCode;
    }

    public String getErrorMsg() {
        return errorMsg;
    }
}