package cn.geelato.lang.exception;


import lombok.Getter;

@Getter
public abstract class CoreException extends RuntimeException {
    private final String errorMsg;
    private final int errorCode;

    public CoreException(int code, String msg) {
        super(msg);
        this.errorMsg = msg;
        this.errorCode=code;
    }
    public CoreException(int code, String msg, Throwable cause) {
        super(msg,cause);
        this.errorMsg = msg;
        this.errorCode=code;
    }


}