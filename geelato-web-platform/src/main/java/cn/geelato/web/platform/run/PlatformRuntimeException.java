package cn.geelato.web.platform.run;


import cn.geelato.lang.exception.CoreException;
import lombok.Getter;
import lombok.Setter;

public class PlatformRuntimeException {

    private CoreException coreException;
    @Setter
    @Getter
    private String logTag;
    @Getter
    private final int errorCode;
    @Getter
    private final String errorMsg;

    public PlatformRuntimeException(CoreException coreException) {
        this.coreException = coreException;
        this.errorCode = coreException.getErrorCode();
        this.errorMsg = coreException.getErrorMsg();
    }

    public PlatformRuntimeException(int code, String msg) {
        super();
        this.errorCode = code;
        this.errorMsg = msg;
    }

    public String getStackTraceDetail() {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : coreException.getStackTrace()) {
            sb.append("[").append(element.toString()).append("]").append("\n");
        }
        return sb.toString();
    }
}
