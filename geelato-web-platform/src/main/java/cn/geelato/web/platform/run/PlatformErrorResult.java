package cn.geelato.web.platform.run;


import cn.geelato.lang.exception.CoreException;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.User;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 平台异常响应载体（POJO）。
 * <p>作为 {@code ApiResult.data} 的一部分序列化返回给前端，携带错误码、文案、日志关联标签、
 * 发生时间与用户、以及在线文档链接等排障所需信息。</p>
 *
 * <p>注意：本类是一个普通 POJO，并非 {@code RuntimeException}（历史命名 {@code PlatformRuntimeException}
 * 存在误导，已重命名为 {@code PlatformErrorResult}）。</p>
 */
public class PlatformErrorResult {

    @Setter
    private CoreException coreException;
    @Setter
    @Getter
    private String logTag;
    @Getter
    private final int errorCode;
    /**
     * 下发给前端的错误文案。
     * <p>默认取 {@link CoreException#getUserMessage()}（用户可见的友好文案，不含 SQL/参数等技术详情）；
     * 开发模式（{@code GlobalContext.getLogStack()} 为 true）下由异常处理器回填完整技术文案。</p>
     */
    @Setter
    @Getter
    private String errorMsg;
    @Getter
    private final String occurUserId;
    @Getter
    private final LocalDateTime occurTime;
    /** 在线文档链接，由 {@link ErrorDocResolver} 解析后注入；前端据此展示"查看文档"入口。 */
    @Setter
    @Getter
    private String docUrl;

    public PlatformErrorResult(CoreException coreException) {
        this.coreException = coreException;
        this.errorCode = coreException.getErrorCode();
        this.errorMsg = coreException.getUserMessage();
        User user = SecurityContext.getCurrentUser();
        this.occurUserId = user != null ? user.getUserId() : "anonymous";
        this.occurTime = LocalDateTime.now();
    }

    public PlatformErrorResult(int code, String msg) {
        super();
        this.errorCode = code;
        this.errorMsg = msg;
        User user = SecurityContext.getCurrentUser();
        this.occurUserId = user != null ? user.getUserId() : "anonymous";
        this.occurTime = LocalDateTime.now();
    }

    public String getStackTraceDetail() {
        if (coreException == null) return "";
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : coreException.getStackTrace()) {
            sb.append("[").append(element.toString()).append("]").append("\n");
        }
        return sb.toString();
    }
}
