package cn.geelato.web.platform.run;


import cn.geelato.lang.exception.CoreException;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.User;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

public class PlatformRuntimeException {

    private CoreException coreException;
    @Setter
    @Getter
    private String logTag;
    @Getter
    private final int errorCode;
    @Getter
    private final String errorMsg;
    @Getter
    private final String occurUserId;
    @Getter
    private final LocalDateTime occurTime;

    public PlatformRuntimeException(CoreException coreException) {
        this.coreException = coreException;
        this.errorCode = coreException.getErrorCode();
        this.errorMsg = coreException.getErrorMsg();
        User user = SecurityContext.getCurrentUser();
        this.occurUserId = user != null ? user.getUserId() : "anonymous";
        this.occurTime = LocalDateTime.now();
    }

    public PlatformRuntimeException(int code, String msg) {
        super();
        this.errorCode = code;
        this.errorMsg = msg;
        User user = SecurityContext.getCurrentUser();
        this.occurUserId = user != null ? user.getUserId() : "anonymous";
        this.occurTime = LocalDateTime.now();
    }

    public String getStackTraceDetail() {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : coreException.getStackTrace()) {
            sb.append("[").append(element.toString()).append("]").append("\n");
        }
        return sb.toString();
    }
}
