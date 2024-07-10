package cn.geelato.web.platform;

import cn.geelato.core.exception.CoreException;

public class PlatformRuntimeException {

    private CoreException coreException;
    private String logTag;
    private final int errorCode;
    private final String errorMsg;

    public PlatformRuntimeException(CoreException coreException) {
        this.coreException=coreException;
        this.errorCode=coreException.getErrorCode();
        this.errorMsg =  coreException.getErrorMsg();
    }

    public PlatformRuntimeException(int code,String msg){
        super();
        this.errorCode=code;
        this.errorMsg=msg;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public String getLogTag() {
        return logTag;
    }

    public void setLogTag(String logTag) {
        this.logTag = logTag;
    }
    public String getStackTraceDetail(){
        StringBuilder sb=new StringBuilder();
        for(StackTraceElement element:coreException.getStackTrace()){
            sb.append("[").append(element.toString()).append("]").append("\n");
        }
        return sb.toString();
    }



}
