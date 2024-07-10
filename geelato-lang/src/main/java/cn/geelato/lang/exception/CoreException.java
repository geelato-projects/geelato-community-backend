package cn.geelato.lang.exception;


import cn.geelato.lang.constants.ApiResultCode;

public class CoreException extends RuntimeException {
    private String msg;
    private int code;

    public CoreException() {
        super();
    }

    public CoreException(String msg) {
        super(msg);
        this.msg = msg;
        this.code = ApiResultCode.ERROR;
    }

    public CoreException(String msg, int code) {
        super(msg);
        this.msg = msg;
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }
}