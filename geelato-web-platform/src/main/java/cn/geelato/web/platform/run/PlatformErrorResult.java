package cn.geelato.web.platform.run;


import cn.geelato.lang.exception.CoreException;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.User;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 平台异常响应载体（POJO）。
 * <p>作为 {@code ApiResult.data} 的一部分序列化返回给前端，按三层职责携带信息：</p>
 * <ul>
 *   <li>{@code msg} / {@code errorMsg} —— 用户可见的友好文案（{@link CoreException#getUserMessage()}）；</li>
 *   <li>{@code errorCode} / {@code logTag} / {@code docUrl} —— 错误码、排障凭据（服务端日志检索键）、在线文档链接；</li>
 *   <li>{@code stackTraceDetail} —— 技术详情（异常消息 + 完整堆栈），由 {@code GlobalContext.getLogStack()}
 *       （默认开启）控制是否下发，用于报障时无需登服务器即可定位问题。</li>
 * </ul>
 *
 * <p>注意：本类是一个普通 POJO，并非 {@code RuntimeException}（历史命名 {@code PlatformRuntimeException}
 * 存在误导，已重命名为 {@code PlatformErrorResult}）。</p>
 */
public class PlatformErrorResult {

    /** 原始异常（不参与 JSON 序列化，仅用于计算 stackTraceDetail；null 时 stackTraceDetail 为空串）。 */
    @Setter
    private Throwable exception;
    @Setter
    @Getter
    private String logTag;
    @Getter
    private final int errorCode;
    /** 用户可见的友好文案（技术详情见 stackTraceDetail）。 */
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
        this.exception = coreException;
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

    /**
     * 技术详情：异常消息（如 SqlExecuteException 的"原因/执行SQL/参数/数据库错误码"）+ 完整堆栈。
     * 无异常引用时返回空串。
     */
    public String getStackTraceDetail() {
        return exception == null ? "" : buildStackTraceDetail(exception);
    }

    /**
     * 技术详情（异常消息 + 完整堆栈）：供响应字段与错误日志落库（platform_exception_log）共用。
     */
    public static String buildStackTraceDetail(Throwable exception) {
        StringBuilder sb = new StringBuilder();
        sb.append(exception.getMessage()).append("\n");
        for (StackTraceElement element : exception.getStackTrace()) {
            sb.append("\tat ").append(element).append("\n");
        }
        return sb.toString();
    }
}
